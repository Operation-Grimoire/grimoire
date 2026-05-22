package io.grimoire.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** [TtsEngine] backed by the on-device [TextToSpeech] engine. Works offline. */
@Singleton
class DeviceTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    override val type = TtsEngineType.DEVICE

    // The device engine has no true pause: the manager re-speaks the current utterance.
    override val canResumeMidUtterance = false

    override var listener: TtsEngine.Listener? = null

    private var tts: TextToSpeech? = null
    private var ready = false

    override suspend fun init(): Boolean {
        if (ready && tts != null) return true
        return suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    engine?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    engine?.setOnUtteranceProgressListener(progressListener)
                    tts = engine
                }
                if (cont.isActive) cont.resume(ready)
            }
        }
    }

    /** Device voices, optionally filtered to [localeTag]; used by the voice picker. */
    suspend fun availableVoices(localeTag: String?): List<TtsVoice> {
        if (!init()) return emptyList()
        val target = localeTag?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
        val voices = tts?.voices ?: return emptyList()
        return voices
            .filter { target == null || it.locale.language == target.language }
            .sortedBy { it.locale.getDisplayName(Locale.ENGLISH) }
            .map { it.toTtsVoice() }
    }

    override fun configure(config: TtsEngineConfig) {
        val engine = tts ?: return
        engine.setSpeechRate(config.rate.coerceIn(0.1f, 3.0f))
        engine.setPitch(config.pitch.coerceIn(0.5f, 2.0f))
        val chosen = config.voiceId?.let { id -> engine.voices?.firstOrNull { it.name == id } }
        if (chosen != null) {
            engine.voice = chosen
        } else {
            val locale = config.localeTag?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
            if (locale != null && engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                engine.setLanguage(locale)
            }
        }
    }

    override fun speak(utteranceId: String, text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun pause() { tts?.stop() }

    override fun resume() { /* no-op: manager re-speaks the current utterance */ }

    override fun stop() { tts?.stop() }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            listener?.onStart(utteranceId)
        }

        override fun onDone(utteranceId: String) {
            listener?.onDone(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            listener?.onError(utteranceId, "Device speech engine error")
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            listener?.onError(utteranceId, "Device speech engine error ($errorCode)")
        }
    }
}

private fun Voice.toTtsVoice(): TtsVoice {
    val qualityLabel = when {
        quality >= Voice.QUALITY_VERY_HIGH -> "Very high quality"
        quality >= Voice.QUALITY_HIGH -> "High quality"
        quality >= Voice.QUALITY_NORMAL -> "Normal quality"
        else -> "Low quality"
    }
    val notInstalled = features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
    val detail = buildString {
        append(qualityLabel)
        if (isNetworkConnectionRequired) append(" · online")
        if (notInstalled) append(" · not installed")
    }
    return TtsVoice(
        id = name,
        displayName = locale.getDisplayName(Locale.ENGLISH).ifBlank { name },
        detail = detail,
        engine = TtsEngineType.DEVICE,
        localeTag = locale.toLanguageTag(),
        needsNetwork = isNetworkConnectionRequired,
        notInstalled = notInstalled,
    )
}
