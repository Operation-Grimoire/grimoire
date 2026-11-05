package io.grimoire.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * [TtsEngine] backed by the ElevenLabs cloud API. Each utterance is synthesized to an
 * MP3 over the network and played with [MediaPlayer], which supports true mid-utterance
 * pause/resume. The next utterance is prefetched while the current one plays so there
 * is no audible gap between paragraphs. Requires a network connection and an API key.
 */
@Singleton
class ElevenLabsTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ElevenLabsApi,
) : TtsEngine {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    // Resolved per use: these singletons outlive an in-app language change.
    private val localizedContext get() = AppLocale.wrap(context)

    override val type = TtsEngineType.ELEVENLABS
    override val canResumeMidUtterance = true
    override var listener: TtsEngine.Listener? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var config: TtsEngineConfig? = null

    private var player: MediaPlayer? = null
    private var speakJob: Job? = null
    private val requests = TtsSynthesisRequests(scope)
    private val playback = TtsAudioPlayback()
    private var playingId: String? = null
    private var playerFile: File? = null
    private val voiceMutex = Mutex()
    private val audioCache by lazy { TtsAudioCache(File(context.cacheDir, "tts-audio")) }
    private var resolvedDefaultVoiceId: String? = null

    private val cacheDir: File by lazy { File(context.cacheDir, "tts").apply { mkdirs() } }

    override suspend fun init(): Boolean = !config?.elevenLabsApiKey.isNullOrBlank()

    override fun configure(config: TtsEngineConfig) {
        if (this.config?.elevenLabsApiKey != config.elevenLabsApiKey) resolvedDefaultVoiceId = null
        this.config = config
    }

    override fun speak(utteranceId: String, text: String) {
        speakJob?.cancel()
        releasePlayer()
        playback.reset()
        playingId = utteranceId
        speakJob = scope.launch {
            var file: File? = null
            try {
                val request = request(utteranceId, text)
                val bytes = request.await()
                requests.consumed(utteranceId, request)
                withContext(Dispatchers.IO) {
                    file = File.createTempFile("utterance-", ".mp3", cacheDir)
                    file!!.writeBytes(bytes)
                }
                currentCoroutineContext().ensureActive()
                play(utteranceId, file!!)
            } catch (ce: CancellationException) {
                file?.delete()
                throw ce
            } catch (e: Exception) {
                file?.delete()
                listener?.onError(
                    utteranceId,
                    e.message ?: localizedContext.getString(R.string.tts_error_request_failed),
                )
            }
        }
    }

    override fun prefetch(utteranceId: String, text: String) {
        request(utteranceId, text)
    }

    private fun request(utteranceId: String, text: String) = requests.get(utteranceId) {
        synthesize(text, config ?: error("ElevenLabs engine not configured"))
    }

    override fun pause() {
        playback.pause()
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    override fun resume() {
        when (playback.resume()) {
            TtsAudioPlayback.Resume.WAIT -> Unit
            TtsAudioPlayback.Resume.START -> runCatching { player?.start() }
            TtsAudioPlayback.Resume.COMPLETE -> playingId?.let { listener?.onDone(it) }
        }
    }

    override fun stop() {
        speakJob?.cancel()
        speakJob = null
        requests.clear()
        playback.reset()
        playingId = null
        releasePlayer()
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }

    override fun release() = stop()

    private suspend fun synthesize(text: String, cfg: TtsEngineConfig): ByteArray {
        val voiceId = voiceMutex.withLock { resolveVoiceId(cfg) }
        val key = ttsAudioCacheKey(cfg, voiceId, text)
        return withContext(Dispatchers.IO) {
            audioCache.get(key) ?: api.synthesize(
                apiKey = cfg.elevenLabsApiKey,
                voiceId = voiceId,
                modelId = cfg.elevenLabsModel,
                text = text,
                speed = cfg.rate,
            ).also {
                currentCoroutineContext().ensureActive()
                audioCache.put(key, it)
            }
        }
    }

    /** The chosen voice, or — when none is set — a default voice usable on any plan. */
    private suspend fun resolveVoiceId(cfg: TtsEngineConfig): String {
        cfg.voiceId?.takeIf { it.isNotBlank() }?.let { return it }
        resolvedDefaultVoiceId?.let { return it }
        val id = api.defaultVoiceId(cfg.elevenLabsApiKey)
            ?: throw IOException(localizedContext.getString(R.string.tts_error_no_default_voice))
        resolvedDefaultVoiceId = id
        return id
    }

    private fun play(utteranceId: String, file: File) {
        releasePlayer()
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        mp.setOnPreparedListener {
            if (player === it && playback.prepared()) {
                it.start()
                listener?.onStart(utteranceId)
            }
        }
        mp.setOnCompletionListener {
            if (player === it) {
                file.delete()
                if (playback.completed()) listener?.onDone(utteranceId)
            }
        }
        mp.setOnErrorListener { failed, what, _ ->
            if (player !== failed) return@setOnErrorListener true
            file.delete()
            listener?.onError(
                utteranceId,
                localizedContext.getString(R.string.tts_error_audio_playback_code, what),
            )
            true
        }
        player = mp
        playerFile = file
        runCatching {
            mp.setDataSource(file.path)
            mp.prepareAsync()
        }.onFailure {
            listener?.onError(
                utteranceId,
                it.message ?: localizedContext.getString(R.string.tts_error_play_audio),
            )
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
        playerFile?.delete()
        playerFile = null
    }
}
