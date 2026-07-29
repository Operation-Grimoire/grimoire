package io.grimoire.app.data.preferences

import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.util.ContentLanguages
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings for the read-aloud feature.
 *
 * [speechRate] and [pitch] are stored as integer percentages (100 = 1.0×) to fit the
 * existing [PreferenceStore.getInt] pattern; callers divide by 100. The per-language
 * voice maps are kept separate per engine because a device [android.speech.tts.Voice]
 * name and an ElevenLabs voice id are not interchangeable.
 */
@Singleton
class TtsPreferences @Inject constructor(store: PreferenceStore) {
    val enabled = store.getBoolean("tts_enabled", true)
    val engine = store.getEnum("tts_engine", TtsEngineType.DEVICE)
    val speechRate = store.getInt("tts_speech_rate_x100", 100)
    val pitch = store.getInt("tts_pitch_x100", 100)
    val autoAdvance = store.getBoolean("tts_auto_advance", true)
    val elevenLabsApiKey = store.getString("tts_elevenlabs_api_key", "")
    val elevenLabsModel = store.getString("tts_elevenlabs_model", "eleven_multilingual_v2")

    /**
     * [io.grimoire.app.util.ContentLanguages.voiceKey] (ISO code) → voice id.
     * Legacy maps keyed by English names heal to codes in [deserializeVoiceMap]
     * and are rewritten as codes on the next save.
     */
    val deviceVoiceByLanguage = store.getObject(
        key = "tts_device_voice_by_language",
        defaultValue = emptyMap<String, String>(),
        serialize = ::serializeVoiceMap,
        deserialize = ::deserializeVoiceMap,
    )

    val cloudVoiceByLanguage = store.getObject(
        key = "tts_cloud_voice_by_language",
        defaultValue = emptyMap<String, String>(),
        serialize = ::serializeVoiceMap,
        deserialize = ::deserializeVoiceMap,
    )
}

// Tab/newline separators are safe: language names and voice ids never contain them.
private fun serializeVoiceMap(map: Map<String, String>): String =
    map.entries.joinToString("\n") { (k, v) -> "$k\t$v" }

private fun deserializeVoiceMap(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.lineSequence().mapNotNull { line ->
        val parts = line.split("\t", limit = 2)
        // Re-key legacy English-name entries to ISO codes; codes pass through.
        if (parts.size == 2 && parts[0].isNotEmpty()) {
            ContentLanguages.voiceKey(parts[0]) to parts[1]
        } else {
            null
        }
    }.toMap()
}
