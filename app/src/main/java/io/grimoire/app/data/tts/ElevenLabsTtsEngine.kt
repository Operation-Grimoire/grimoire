package io.grimoire.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

    override val type = TtsEngineType.ELEVENLABS
    override val canResumeMidUtterance = true
    override var listener: TtsEngine.Listener? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var config: TtsEngineConfig? = null

    private var player: MediaPlayer? = null
    private var speakJob: Job? = null
    private val prefetched = ConcurrentHashMap<String, ByteArray>()

    private val cacheDir: File by lazy { File(context.cacheDir, "tts").apply { mkdirs() } }

    override suspend fun init(): Boolean = !config?.elevenLabsApiKey.isNullOrBlank()

    override fun configure(config: TtsEngineConfig) {
        this.config = config
    }

    override fun speak(utteranceId: String, text: String) {
        speakJob?.cancel()
        speakJob = scope.launch {
            try {
                val bytes = prefetched.remove(utteranceId) ?: synthesize(text)
                val file = writeTemp(utteranceId, bytes)
                withContext(Dispatchers.Main) { play(utteranceId, file) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                listener?.onError(utteranceId, e.message ?: "ElevenLabs request failed")
            }
        }
    }

    override fun prefetch(utteranceId: String, text: String) {
        if (prefetched.containsKey(utteranceId)) return
        scope.launch {
            runCatching { synthesize(text) }.onSuccess { prefetched[utteranceId] = it }
        }
    }

    override fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    override fun resume() {
        runCatching { player?.start() }
    }

    override fun stop() {
        speakJob?.cancel()
        speakJob = null
        prefetched.clear()
        releasePlayer()
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }

    override fun release() = stop()

    private suspend fun synthesize(text: String): ByteArray {
        val cfg = config ?: error("ElevenLabs engine not configured")
        return api.synthesize(
            apiKey = cfg.elevenLabsApiKey,
            voiceId = cfg.voiceId?.takeIf { it.isNotBlank() } ?: DEFAULT_VOICE_ID,
            modelId = cfg.elevenLabsModel,
            text = text,
            speed = cfg.rate,
        )
    }

    private fun writeTemp(utteranceId: String, bytes: ByteArray): File {
        val safe = utteranceId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(cacheDir, "u_$safe.mp3").apply { writeBytes(bytes) }
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
            it.start()
            listener?.onStart(utteranceId)
        }
        mp.setOnCompletionListener {
            file.delete()
            listener?.onDone(utteranceId)
        }
        mp.setOnErrorListener { _, what, _ ->
            file.delete()
            listener?.onError(utteranceId, "Audio playback error ($what)")
            true
        }
        player = mp
        runCatching {
            mp.setDataSource(file.path)
            mp.prepareAsync()
        }.onFailure {
            listener?.onError(utteranceId, it.message ?: "Could not play audio")
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

    companion object {
        /** ElevenLabs "Rachel" — a stable public voice used when none is chosen. */
        const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"
    }
}
