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
import io.grimoire.app.R
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.preferences.TtsPreferences
import io.grimoire.app.util.AppLocale
import io.grimoire.app.util.ContentLanguages
import io.grimoire.reading.state.ReadingStateFacade
import io.grimoire.reading.storage.ReadingStateDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    readingStateDao: ReadingStateDao,
    readingStateFacade: ReadingStateFacade,
) {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    // Resolved per use: these singletons outlive an in-app language change.
    private val localizedContext get() = AppLocale.wrap(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val completionWriter = TtsCompletionWriter(scope, readingStateDao, readingStateFacade)
    internal val completionNotice = completionWriter.notice
    private val playbackTask = TtsPlaybackTask(scope) { e ->
        fail(e.message ?: localizedContext.getString(R.string.tts_error_start_playback))
    }

    private val _state = MutableStateFlow(TtsPlaybackState.IDLE)
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow<TtsNowPlaying?>(null)
    val nowPlaying: StateFlow<TtsNowPlaying?> = _nowPlaying.asStateFlow()

    private val _chapterContent = MutableStateFlow<TtsChapterContent?>(null)
    val chapterContent: StateFlow<TtsChapterContent?> = _chapterContent.asStateFlow()

    private val _progress = MutableStateFlow(TtsProgress())
    val progress: StateFlow<TtsProgress> = _progress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var mediaSession: MediaSessionCompat? = null

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focusRequest: AudioFocusRequestCompat? = null
    private val focusResume = TtsFocusResumePolicy()
    private var noisyRegistered = false

    // Request context.
    private var pkg = ""
    private var novelId = 0L
    private var novelTitle = ""
    private var novelLanguage: String? = null
    private var chapters: List<ChapterEntity> = emptyList()
    private var chapterIndex = 0
    private var pendingChapterUrl: String? = null
    private var preloadedChapter: TtsChapterInput? = null

    // Current chapter playback.
    private var activeEngine: TtsEngine? = null
    private var engineInitialized = false
    private var utterances: List<Utterance> = emptyList()
    private var utteranceIndex = 0
    private var generation = 0
    private var speechAttempt = 0
    private val callbacks = TtsUtteranceCallbacks()

    // ---- Public entry points -------------------------------------------------

    /** Records what to play. Called by [TtsController] before the service is started. */
    fun prepare(
        pkg: String,
        novelId: Long,
        chapterUrl: String,
        chapters: List<ChapterEntity>,
        startIndex: Int,
        input: TtsChapterInput?,
    ) {
        playbackTask.cancel()
        engineInitialized = false
        retireChapter()
        activeEngine?.stop()
        this.pkg = pkg
        this.novelId = novelId
        this.chapters = chapters
        this.chapterIndex = startIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        this.pendingChapterUrl = chapterUrl
        this.preloadedChapter = input
        this.novelTitle = ""
        _errorMessage.value = null
        _progress.value = TtsProgress()
        _state.value = TtsPlaybackState.LOADING
        updateNowPlaying()
    }

    /** Begins playback. Called by [TtsPlaybackService] once it is in the foreground. */
    fun startPlayback() {
        playbackTask.launch {
            engineInitialized = false
            _state.value = TtsPlaybackState.LOADING
            val engineType = ttsPreferences.engine.changes().first()
            val engine = if (engineType == TtsEngineType.ELEVENLABS) cloudEngine else deviceEngine
            engine.listener = engineListener
            activeEngine = engine

            if (chapters.isEmpty()) chapters = chapterLoader.loadChapterList(novelId)
            if (chapters.isEmpty()) return@launch fail(localizedContext.getString(R.string.tts_error_no_chapters))
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
            val initialized = engine.init()
            currentCoroutineContext().ensureActive()
            if (!initialized) {
                return@launch fail(
                    if (engine.type == TtsEngineType.ELEVENLABS) {
                        localizedContext.getString(R.string.tts_error_missing_api_key)
                    } else {
                        localizedContext.getString(R.string.tts_error_device_unavailable)
                    },
                )
            }
            engineInitialized = true
            val input = preloadedChapter.takeIf { chapters[chapterIndex].url == pendingChapterUrl }
            preloadedChapter = null
            startChapter(chapterIndex, input)
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
        focusResume.clear()
        if (_state.value == TtsPlaybackState.PAUSED) {
            abandonAudioFocus()
            unregisterNoisy()
            return
        }
        pausePlayback(releaseFocus = true)
    }

    private fun pausePlayback(releaseFocus: Boolean) {
        if (_state.value != TtsPlaybackState.PLAYING) return
        _state.value = TtsPlaybackState.PAUSED
        val engine = activeEngine
        if (engine?.canResumeMidUtterance == false && callbacks.finished == null) {
            callbacks.clear()
            speechAttempt++
        }
        engine?.pause()
        if (releaseFocus) {
            abandonAudioFocus()
            unregisterNoisy()
        }
        updateMediaSession()
    }

    fun resume() {
        if (_state.value != TtsPlaybackState.PAUSED) return
        focusResume.clear()
        val engine = activeEngine ?: return
        if (!requestAudioFocus()) return
        registerNoisy()
        _state.value = TtsPlaybackState.PLAYING
        updateMediaSession()
        if (callbacks.finished != null) {
            advanceCompletedUtterance()
        } else if (engine.canResumeMidUtterance) {
            engine.resume()
        } else {
            speakCurrent()
        }
    }

    fun stop() {
        playbackTask.cancel()
        engineInitialized = false
        retireChapter()
        preloadedChapter = null
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
        if (!engineInitialized) return
        if (chapterIndex >= chapters.size - 1) return
        playbackTask.launch { startChapter(chapterIndex + 1, null, startIndex = 0) }
    }

    fun skipPrevious() {
        if (!engineInitialized) return
        playbackTask.launch {
            // Restart the current chapter if well into it, otherwise go to the previous one.
            val target = if (utteranceIndex > 2 || chapterIndex == 0) chapterIndex else chapterIndex - 1
            startChapter(target, null, startIndex = 0)
        }
    }

    // ---- Service hooks -------------------------------------------------------

    fun release() {
        stop()
        deviceEngine.listener = null
        cloudEngine.listener = null
        deviceEngine.release()
        cloudEngine.release()
        activeEngine = null
        mediaSession?.release()
        mediaSession = null
    }

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
    internal fun dismissCompletionError() = completionWriter.dismiss()
    internal fun retryCompletionSave() { completionWriter.retry() }

    // ---- Chapter playback ---------------------------------------------------

    private fun retireChapter() {
        generation++
        speechAttempt = 0
        callbacks.clear()
        completionWriter.retire()
        _chapterContent.value = null
    }

    private suspend fun startChapter(index: Int, preloaded: TtsChapterInput?, startIndex: Int? = null) {
        retireChapter()
        activeEngine?.stop()
        chapterIndex = index
        val chapter = chapters.getOrNull(index) ?: return fail(localizedContext.getString(R.string.tts_error_chapter_not_found))
        _state.value = TtsPlaybackState.LOADING
        updateNowPlaying()

        val input = preloaded?.takeIf { it.pages.isNotEmpty() } ?: run {
            val loaded = chapterLoader.loadChapter(pkg, chapter)
            withContext(Dispatchers.Default) { TtsChapterInput.fromLoaded(loaded) }
        }
        currentCoroutineContext().ensureActive()
        _nowPlaying.value?.let { _chapterContent.value = TtsChapterContent(it, input.pages, input.observation) }
        val locale = languageResolver.resolveLocale(novelLanguage)
        utterances = TtsTextChunker.chunk(input.pages, locale)
        if (utterances.isEmpty()) {
            if (autoAdvance() && chapterIndex < chapters.size - 1) {
                return startChapter(chapterIndex + 1, null, startIndex = 0)
            }
            return fail(localizedContext.getString(R.string.tts_error_no_readable_text))
        }

        utteranceIndex = chapterStartUtterance(chapter.read, chapter.readProgress, utterances.size, startIndex)
        configureEngine(locale)
        currentCoroutineContext().ensureActive()
        completionWriter.bind(input)
        currentCoroutineContext().ensureActive()
        if (!requestAudioFocus()) return fail(localizedContext.getString(R.string.tts_error_audio_focus))
        registerNoisy()
        mediaSession?.isActive = true
        _state.value = TtsPlaybackState.PLAYING
        speakCurrent()
    }

    private fun speakCurrent() {
        val engine = activeEngine ?: return
        val utterance = utterances.getOrNull(utteranceIndex)
        if (utterance == null) {
            fail(localizedContext.getString(R.string.tts_error_no_readable_text))
            return
        }
        _progress.value = TtsProgress(utteranceIndex, utterances.size, utterance.pageIndex)
        updateMediaSession()
        val id = uid(utteranceIndex)
        callbacks.speak(id)
        engine.speak(id, utterance.text)
        utterances.getOrNull(utteranceIndex + 1)?.let {
            engine.prefetch(uid(utteranceIndex + 1), it.text)
        }
    }

    private suspend fun endOfChapter(completed: TtsUtteranceCallbacks.Finished) {
        completionWriter.complete(completed, utteranceIndex, utterances.size)?.join()
        val advance = autoAdvance()
        currentCoroutineContext().ensureActive()
        if (_state.value != TtsPlaybackState.PLAYING) return
        if (advance && chapterIndex < chapters.size - 1) {
            startChapter(chapterIndex + 1, null, startIndex = 0)
        } else {
            stop()
        }
    }

    private val engineListener = object : TtsEngine.Listener {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
            scope.launch {
                val playing = _state.value == TtsPlaybackState.PLAYING
                if (!playing && _state.value != TtsPlaybackState.PAUSED) return@launch
                val acceptedAt = (System.currentTimeMillis() / 1000) * 1000
                callbacks.finish(utteranceId, playing, acceptedAt) ?: return@launch
                if (playing) advanceCompletedUtterance()
            }
        }

        override fun onError(utteranceId: String, message: String) {
            scope.launch {
                if (!callbacks.isCurrent(utteranceId)) return@launch
                fail(message)
            }
        }
    }

    private fun advanceCompletedUtterance() {
        val completed = callbacks.finished ?: return
        playbackTask.launch {
            persistProgress()
            currentCoroutineContext().ensureActive()
            if (_state.value != TtsPlaybackState.PLAYING || completed !== callbacks.finished ||
                completed.id != uid(utteranceIndex)
            ) return@launch
            if (utteranceIndex >= utterances.lastIndex) {
                endOfChapter(completed)
            } else {
                utteranceIndex++
                speakCurrent()
            }
        }
    }

    private suspend fun persistProgress() {
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        if (utterances.isEmpty()) return
        val fraction = ((utteranceIndex + 1).toFloat() / utterances.size).coerceIn(0f, 1f)
        try {
            chapterDao.setReadProgress(chapter.id, fraction)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep compatibility progress best-effort, independently of guarded completion.
        }
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
        playbackTask.cancel()
        engineInitialized = false
        retireChapter()
        preloadedChapter = null
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
        val request = focusRequest ?: AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
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
        focusResume.clear()
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (focusResume.transientLoss(_state.value == TtsPlaybackState.PLAYING)) {
                    pausePlayback(releaseFocus = false)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (focusResume.gain()) resume()
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

    private fun uid(index: Int) = "$generation:$index:$speechAttempt"
}
