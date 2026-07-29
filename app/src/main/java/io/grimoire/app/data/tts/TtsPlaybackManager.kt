package io.grimoire.app.data.tts

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.preferences.TtsPreferences
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for read-aloud playback. Owns the active [TtsEngine], the
 * utterance queue, the [MediaSessionCompat], audio focus, and the playback state flows.
 *
 * It is engine-agnostic and event-driven: it tells the engine to [TtsEngine.speak] one
 * utterance and, on [TtsEngine.Listener.onDone], advances to the next utterance or the
 * next chapter. A monotonically increasing [generation] tags utterance ids so callbacks
 * that arrive after a stop/skip are discarded.
 *
 * Being a [Singleton], the same instance is shared by [TtsPlaybackService] and the UI,
 * so playback state survives navigation and is observable from anywhere.
 */
@Singleton
class TtsPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterLoader: TtsChapterLoader,
    private val languageResolver: TtsLanguageResolver,
    private val deviceEngine: DeviceTtsEngine,
    private val cloudEngine: ElevenLabsTtsEngine,
    private val ttsPreferences: TtsPreferences,
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(TtsPlaybackState.IDLE)
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow<TtsNowPlaying?>(null)
    val nowPlaying: StateFlow<TtsNowPlaying?> = _nowPlaying.asStateFlow()

    private val _progress = MutableStateFlow(TtsProgress())
    val progress: StateFlow<TtsProgress> = _progress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var mediaSession: MediaSessionCompat? = null

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focusRequest: AudioFocusRequestCompat? = null
    private var pausedByFocusLoss = false
    private var noisyRegistered = false

    // Request context.
    private var pkg = ""
    private var novelId = 0L
    private var novelTitle = ""
    private var novelLanguage: String? = null
    private var chapters: List<ChapterEntity> = emptyList()
    private var chapterIndex = 0
    private var pendingChapterUrl: String? = null
    private var preloadedPages: List<NovelPage> = emptyList()

    // Current chapter playback.
    private var activeEngine: TtsEngine? = null
    private var utterances: List<Utterance> = emptyList()
    private var utteranceIndex = 0
    private var generation = 0

    // ---- Public entry points -------------------------------------------------

    /** Records what to play. Called by [TtsController] before the service is started. */
    fun prepare(
        pkg: String,
        novelId: Long,
        chapterUrl: String,
        chapters: List<ChapterEntity>,
        startIndex: Int,
        pages: List<NovelPage>,
    ) {
        generation++
        activeEngine?.stop()
        this.pkg = pkg
        this.novelId = novelId
        this.chapters = chapters
        this.chapterIndex = startIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        this.pendingChapterUrl = chapterUrl
        this.preloadedPages = pages
        this.novelTitle = ""
        _errorMessage.value = null
        _progress.value = TtsProgress()
        _state.value = TtsPlaybackState.LOADING
        updateNowPlaying()
    }

    /** Begins playback. Called by [TtsPlaybackService] once it is in the foreground. */
    fun startPlayback() {
        scope.launch {
            try {
                _state.value = TtsPlaybackState.LOADING
                val engineType = ttsPreferences.engine.changes().first()
                val engine = if (engineType == TtsEngineType.ELEVENLABS) cloudEngine else deviceEngine
                engine.listener = engineListener
                activeEngine = engine

                if (chapters.isEmpty()) chapters = chapterLoader.loadChapterList(novelId)
                if (chapters.isEmpty()) return@launch fail("This novel has no chapters to read")
                pendingChapterUrl?.let { url ->
                    val match = chapters.indexOfFirst { it.url == url }
                    if (match >= 0) chapterIndex = match
                }

                novelDao.getById(novelId)?.let {
                    novelTitle = it.title
                    novelLanguage = it.language
                }
                novelDao.updateLastReadAt(novelId, System.currentTimeMillis())
                updateNowPlaying()

                // Configure before init() so the cloud engine sees the API key.
                configureEngine(languageResolver.resolveLocale(novelLanguage))
                if (!engine.init()) {
                    return@launch fail(
                        if (engine.type == TtsEngineType.ELEVENLABS) {
                            "Add an ElevenLabs API key in Settings → Text-to-speech"
                        } else {
                            "The device speech engine is unavailable"
                        },
                    )
                }
                startChapter(chapterIndex, preloadedPages)
            } catch (e: Exception) {
                fail(e.message ?: "Could not start playback")
            }
        }
    }

    fun togglePlayPause() {
        when (_state.value) {
            TtsPlaybackState.PLAYING -> pause()
            TtsPlaybackState.PAUSED -> resume()
            else -> Unit
        }
    }

    fun pause() {
        if (_state.value != TtsPlaybackState.PLAYING) return
        activeEngine?.pause()
        _state.value = TtsPlaybackState.PAUSED
        abandonAudioFocus()
        unregisterNoisy()
        updateMediaSession()
    }

    fun resume() {
        if (_state.value != TtsPlaybackState.PAUSED) return
        val engine = activeEngine ?: return
        if (!requestAudioFocus()) return
        registerNoisy()
        _state.value = TtsPlaybackState.PLAYING
        updateMediaSession()
        if (engine.canResumeMidUtterance) engine.resume() else speakCurrent()
    }

    fun stop() {
        generation++
        activeEngine?.stop()
        _state.value = TtsPlaybackState.IDLE
        _nowPlaying.value = null
        _progress.value = TtsProgress()
        utterances = emptyList()
        abandonAudioFocus()
        unregisterNoisy()
        mediaSession?.isActive = false
        updateMediaSession()
    }

    fun skipNext() {
        if (chapterIndex >= chapters.size - 1) return
        scope.launch { runCatching { startChapter(chapterIndex + 1, null) } }
    }

    fun skipPrevious() {
        scope.launch {
            // Restart the current chapter if well into it, otherwise go to the previous one.
            val target = if (utteranceIndex > 2 || chapterIndex == 0) chapterIndex else chapterIndex - 1
            runCatching { startChapter(target, null) }
        }
    }

    // ---- Service hooks -------------------------------------------------------

    fun buildNotification(): Notification {
        ensureSession()
        return TtsNotificationBuilder.build(
            context, mediaSession!!.sessionToken, _nowPlaying.value, _state.value,
        )
    }

    fun handleMediaButton(intent: Intent?) {
        ensureSession()
        androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
    }

    fun consumeError(): String? = _errorMessage.value.also { _errorMessage.value = null }

    // ---- Chapter playback ---------------------------------------------------

    private suspend fun startChapter(index: Int, preloaded: List<NovelPage>?) {
        activeEngine?.stop()
        generation++
        chapterIndex = index
        val chapter = chapters.getOrNull(index) ?: return fail("Chapter not found")
        _state.value = TtsPlaybackState.LOADING
        updateNowPlaying()

        val pages = preloaded?.takeIf { it.isNotEmpty() } ?: chapterLoader.loadPages(pkg, chapter)
        val locale = languageResolver.resolveLocale(novelLanguage)
        utterances = TtsTextChunker.chunk(pages, locale)
        if (utterances.isEmpty()) {
            if (autoAdvance() && chapterIndex < chapters.size - 1) {
                return startChapter(chapterIndex + 1, null)
            }
            return fail("This chapter has no readable text")
        }

        utteranceIndex = resumeUtterance(chapter)
        configureEngine(locale)
        if (!requestAudioFocus()) return fail("Could not get audio focus")
        registerNoisy()
        mediaSession?.isActive = true
        _state.value = TtsPlaybackState.PLAYING
        speakCurrent()
    }

    private fun speakCurrent() {
        val engine = activeEngine ?: return
        val utterance = utterances.getOrNull(utteranceIndex)
        if (utterance == null) {
            scope.launch { endOfChapter() }
            return
        }
        _progress.value = TtsProgress(utteranceIndex, utterances.size, utterance.pageIndex)
        updateMediaSession()
        engine.speak(uid(utteranceIndex), utterance.text)
        utterances.getOrNull(utteranceIndex + 1)?.let {
            engine.prefetch(uid(utteranceIndex + 1), it.text)
        }
    }

    private suspend fun endOfChapter() {
        chapters.getOrNull(chapterIndex)?.let { chapter ->
            runCatching {
                chapterDao.setReadProgress(chapter.id, 1f)
                chapterDao.setRead(chapter.id, true)
            }
        }
        if (autoAdvance() && chapterIndex < chapters.size - 1) {
            startChapter(chapterIndex + 1, null)
        } else {
            stop()
        }
    }

    private val engineListener = object : TtsEngine.Listener {
        override fun onStart(utteranceId: String) {
            scope.launch {
                if (parseGeneration(utteranceId) != generation) return@launch
                if (_state.value == TtsPlaybackState.LOADING) {
                    _state.value = TtsPlaybackState.PLAYING
                    updateMediaSession()
                }
            }
        }

        override fun onDone(utteranceId: String) {
            scope.launch {
                if (parseGeneration(utteranceId) != generation) return@launch
                persistProgress()
                utteranceIndex++
                if (utteranceIndex >= utterances.size) endOfChapter() else speakCurrent()
            }
        }

        override fun onError(utteranceId: String, message: String) {
            scope.launch {
                if (parseGeneration(utteranceId) != generation) return@launch
                fail(message)
            }
        }
    }

    private suspend fun persistProgress() {
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        if (utterances.isEmpty()) return
        val fraction = ((utteranceIndex + 1).toFloat() / utterances.size).coerceIn(0f, 1f)
        runCatching { chapterDao.setReadProgress(chapter.id, fraction) }
    }

    private fun resumeUtterance(chapter: ChapterEntity): Int {
        if (chapter.read) return 0
        val progress = chapter.readProgress
        if (progress <= 0.01f || progress >= 0.97f) return 0
        return (progress * utterances.size).toInt().coerceIn(0, utterances.size - 1)
    }

    private suspend fun configureEngine(locale: java.util.Locale) {
        val engine = activeEngine ?: return
        val voiceMap = if (engine.type == TtsEngineType.ELEVENLABS) {
            ttsPreferences.cloudVoiceByLanguage.changes().first()
        } else {
            ttsPreferences.deviceVoiceByLanguage.changes().first()
        }
        engine.configure(
            TtsEngineConfig(
                voiceId = selectVoiceId(voiceMap, locale),
                localeTag = locale.toLanguageTag(),
                rate = ttsPreferences.speechRate.changes().first() / 100f,
                pitch = ttsPreferences.pitch.changes().first() / 100f,
                elevenLabsApiKey = ttsPreferences.elevenLabsApiKey.changes().first(),
                elevenLabsModel = ttsPreferences.elevenLabsModel.changes().first(),
            ),
        )
    }

    /**
     * The voice picked for this novel's language, or null for the engine default.
     *
     * The per-language map is keyed by [ContentLanguages.voiceKey] (the ISO code);
     * voiceKey also resolves the English names novels store. Try the novel's own
     * language first, then the resolved [locale]'s code and English display name —
     * so a tag-language or unlabelled novel (which [TtsLanguageResolver] maps to
     * the device locale) still uses the chosen voice instead of the default.
     */
    private fun selectVoiceId(voiceMap: Map<String, String>, locale: java.util.Locale): String? {
        novelLanguage?.trim()?.takeIf { it.isNotEmpty() }?.let { lang ->
            voiceMap[ContentLanguages.voiceKey(lang)]?.let { return it }
        }
        locale.language.takeIf { it.isNotBlank() }?.let { code ->
            voiceMap[ContentLanguages.voiceKey(code)]?.let { return it }
        }
        val localeName = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
        if (localeName.isNotBlank()) {
            voiceMap[ContentLanguages.voiceKey(localeName)]?.let { return it }
        }
        return null
    }

    private suspend fun autoAdvance(): Boolean = ttsPreferences.autoAdvance.changes().first()

    private fun fail(message: String) {
        generation++
        activeEngine?.stop()
        _state.value = TtsPlaybackState.ERROR
        _errorMessage.value = message
        abandonAudioFocus()
        unregisterNoisy()
        mediaSession?.isActive = false
        updateMediaSession()
    }

    private fun updateNowPlaying() {
        val chapter = chapters.getOrNull(chapterIndex)
        _nowPlaying.value = chapter?.let {
            TtsNowPlaying(pkg, novelId, novelTitle, it.url, it.name)
        }
    }

    // ---- Media session ------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun ensureSession() {
        if (mediaSession != null) return
        mediaSession = MediaSessionCompat(context, "GrimoireTts").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(mediaSessionCallback)
        }
        updateMediaSession()
        mediaSession?.isActive = true
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = resume()
        override fun onPause() = pause()
        override fun onStop() = stop()
        override fun onSkipToNext() = skipNext()
        override fun onSkipToPrevious() = skipPrevious()
    }

    private fun updateMediaSession() {
        val session = mediaSession ?: return
        val stateCompat = when (_state.value) {
            TtsPlaybackState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            TtsPlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            TtsPlaybackState.LOADING -> PlaybackStateCompat.STATE_BUFFERING
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(
                    stateCompat,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    if (_state.value == TtsPlaybackState.PLAYING) 1f else 0f,
                )
                .build(),
        )
        val np = _nowPlaying.value
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, np?.chapterName ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, np?.novelTitle ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, np?.novelTitle ?: "")
                .build(),
        )
    }

    // ---- Audio focus & becoming-noisy --------------------------------------

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return AudioManagerCompat.requestAudioFocus(audioManager, request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        focusRequest = null
        pausedByFocusLoss = false
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_state.value == TtsPlaybackState.PLAYING) {
                    pause()
                    pausedByFocusLoss = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    resume()
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            context, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    private fun uid(index: Int) = "$generation:$index"
    private fun parseGeneration(id: String) = id.substringBefore(':').toIntOrNull() ?: -1
}
