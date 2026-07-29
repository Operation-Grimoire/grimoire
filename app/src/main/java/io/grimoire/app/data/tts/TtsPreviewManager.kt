package io.grimoire.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * Plays a short voice sample for the per-language voice picker so a voice can be auditioned
 * without leaving Settings for the reader.
 *
 * Deliberately self-contained — it does **not** reuse the [TtsEngine] singletons that
 * [TtsPlaybackManager] drives. Sharing them would mutate the active reader's voice/rate
 * (device engine) or wipe its prefetched audio cache (ElevenLabs `stop()` clears the shared
 * `tts/` dir). This manager keeps its own [TextToSpeech] instance, its own `tts-preview/`
 * cache dir, and requests no audio focus, so previewing never disturbs an ongoing read-aloud.
 *
 * One preview plays at a time: starting another, or toggling the active one, stops the first.
 */
@Singleton
class TtsPreviewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ElevenLabsApi,
) {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    private val localizedContext = AppLocale.wrap(context)

    /** Where a preview is in its lifecycle once a row is active. */
    enum class Phase { LOADING, PLAYING }

    /**
     * [key] identifies the picker row whose voice is being previewed (null = nothing playing).
     * Rows pass their voice id, or [SYSTEM_DEFAULT_KEY] for the "System default" row.
     */
    data class State(
        val key: String? = null,
        val phase: Phase = Phase.LOADING,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private var job: Job? = null

    /**
     * Bumped on every start/stop. Async callbacks (TTS utterance progress, MediaPlayer
     * prepared/completion, network results) carry the token they were started with and are
     * ignored once it changes, so a stale finish can't reset a newer preview.
     */
    private var token = 0

    private val cacheDir: File by lazy { File(context.cacheDir, "tts-preview").apply { mkdirs() } }

    /**
     * Toggles the preview for [key]: stops if that row is already active, otherwise stops any
     * current preview and starts a new one. [engine] selects the backend; [config] supplies the
     * voice/locale/rate/pitch and (for ElevenLabs) the API key and model.
     */
    fun toggle(key: String, engine: TtsEngineType, config: TtsEngineConfig, sampleText: String) {
        if (_state.value.key == key) {
            stop()
            return
        }
        stop()
        val myToken = token
        _state.value = State(key = key, phase = Phase.LOADING)
        when (engine) {
            TtsEngineType.DEVICE -> speakDevice(key, myToken, config, sampleText)
            TtsEngineType.ELEVENLABS -> speakCloud(key, myToken, config, sampleText)
        }
    }

    /** Stops any active preview and returns to idle. Safe to call when nothing is playing. */
    fun stop() {
        token++
        job?.cancel()
        job = null
        runCatching { tts?.stop() }
        releasePlayer()
        if (_state.value != State()) _state.value = State()
    }

    /** Clears a surfaced error after the UI has shown it. */
    fun clearError() {
        if (_state.value.error != null) _state.value = State()
    }

    private fun speakDevice(key: String, myToken: Int, config: TtsEngineConfig, text: String) {
        job = scope.launch {
            val ready = ensureTts()
            if (myToken != token) return@launch
            val engine = tts
            if (!ready || engine == null) {
                fail(myToken, "On-device speech engine is unavailable")
                return@launch
            }
            engine.setSpeechRate(config.rate.coerceIn(0.1f, 3.0f))
            engine.setPitch(config.pitch.coerceIn(0.5f, 2.0f))
            val chosen = config.voiceId?.let { id -> engine.voices?.firstOrNull { it.name == id } }
            if (chosen != null) {
                engine.voice = chosen
            } else {
                val locale = config.localeTag
                    ?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
                if (locale != null && engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.setLanguage(locale)
                }
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, previewUtteranceId(myToken))
        }
    }

    private fun speakCloud(key: String, myToken: Int, config: TtsEngineConfig, text: String) {
        job = scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    val voiceId = config.voiceId?.takeIf { it.isNotBlank() }
                        ?: api.defaultVoiceId(config.elevenLabsApiKey)
                        ?: throw IllegalStateException(
                            "No default ElevenLabs voice is available — pick one to preview",
                        )
                    val bytes = api.synthesize(
                        apiKey = config.elevenLabsApiKey,
                        voiceId = voiceId,
                        modelId = config.elevenLabsModel,
                        text = text,
                        speed = config.rate,
                    )
                    File(cacheDir, "preview.mp3").apply { writeBytes(bytes) }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                fail(myToken, e.message ?: localizedContext.getString(R.string.tts_error_request_failed))
                return@launch
            }
            if (myToken != token) return@launch
            playFile(myToken, file)
        }
    }

    private fun playFile(myToken: Int, file: File) {
        releasePlayer()
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        mp.setOnPreparedListener {
            if (myToken != token) {
                it.release()
                return@setOnPreparedListener
            }
            it.start()
            _state.value = _state.value.copy(phase = Phase.PLAYING)
        }
        mp.setOnCompletionListener {
            file.delete()
            if (myToken == token) _state.value = State()
        }
        mp.setOnErrorListener { _, what, _ ->
            file.delete()
            if (myToken == token) fail(myToken, "Audio playback error ($what)")
            true
        }
        player = mp
        runCatching {
            mp.setDataSource(file.path)
            mp.prepareAsync()
        }.onFailure {
            fail(myToken, it.message ?: localizedContext.getString(R.string.tts_error_play_preview))
        }
    }

    private fun fail(myToken: Int, message: String) {
        if (myToken != token) return
        token++
        _state.value = State(error = message)
    }

    private suspend fun ensureTts(): Boolean {
        if (ttsReady && tts != null) return true
        return suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    engine?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    engine?.setOnUtteranceProgressListener(progressListener)
                    tts = engine
                }
                if (cont.isActive) cont.resume(ttsReady)
            }
        }
    }

    private fun releasePlayer() {
        player?.let { mp ->
            runCatching {
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                mp.setOnPreparedListener(null)
                mp.reset()
                mp.release()
            }
        }
        player = null
    }

    private fun previewUtteranceId(token: Int): String = "preview-$token"

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            if (utteranceId == previewUtteranceId(token)) {
                _state.value = _state.value.copy(phase = Phase.PLAYING)
            }
        }

        override fun onDone(utteranceId: String) {
            if (utteranceId == previewUtteranceId(token)) _state.value = State()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            if (utteranceId == previewUtteranceId(token)) {
                fail(token, "On-device speech engine error")
            }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            if (utteranceId == previewUtteranceId(token)) {
                fail(token, "On-device speech engine error ($errorCode)")
            }
        }
    }

    companion object {
        /** [State.key] used by the picker's "System default" row (whose voice id is null). */
        const val SYSTEM_DEFAULT_KEY = "__system_default__"
    }
}
