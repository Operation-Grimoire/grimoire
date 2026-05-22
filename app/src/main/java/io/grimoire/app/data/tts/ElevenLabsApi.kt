package io.grimoire.app.data.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Thin client for the ElevenLabs Text-to-Speech REST API. */
@Singleton
class ElevenLabsApi @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lists the voices on the account behind [apiKey]. Uses the v2 endpoint, which
     * reports a [ApiVoice.voiceType] per voice — unlike v1's `category`, this reliably
     * tells "default"/"personal" voices (usable on any plan) apart from "community"
     * Voice Library voices, which the API rejects for free accounts.
     */
    suspend fun listVoices(apiKey: String): List<TtsVoice> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add an ElevenLabs API key first." }
        val request = Request.Builder()
            .url("$BASE_URL/v2/voices?page_size=100")
            .header("xi-api-key", apiKey)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(response.code, body))
            json.decodeFromString(VoicesResponse.serializer(), body).voices
                // Default/personal voices work on every plan — list them before the
                // paid-only Voice Library ("community") voices.
                .sortedBy { voiceTypeRank(it.voiceType) }
                .map { v ->
                    TtsVoice(
                        id = v.voiceId,
                        displayName = v.name,
                        detail = voiceTypeLabel(v.voiceType),
                        engine = TtsEngineType.ELEVENLABS,
                        needsNetwork = true,
                    )
                }
        }
    }

    /**
     * Synthesizes [text] with [voiceId] and returns the MP3 bytes.
     * [speed] is clamped to the range ElevenLabs accepts (0.7–1.2).
     */
    suspend fun synthesize(
        apiKey: String,
        voiceId: String,
        modelId: String,
        text: String,
        speed: Float,
    ): ByteArray = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add an ElevenLabs API key first." }
        val settings = VoiceSettings(speed = speed.coerceIn(0.7f, 1.2f))
        val payload = SynthesisRequest(text = text, modelId = modelId, voiceSettings = settings)
        val request = Request.Builder()
            .url("$BASE_URL/v1/text-to-speech/$voiceId")
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/mpeg")
            .post(json.encodeToString(SynthesisRequest.serializer(), payload).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(errorMessage(response.code, response.body?.string().orEmpty()))
            }
            response.body?.bytes() ?: throw IOException("ElevenLabs returned an empty response")
        }
    }

    /** Returns the account's character-quota usage for the current billing period. */
    suspend fun getUsage(apiKey: String): ElevenLabsUsage = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add an ElevenLabs API key first." }
        val request = Request.Builder()
            .url("$BASE_URL/v1/user/subscription")
            .header("xi-api-key", apiKey)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(response.code, body))
            val dto = json.decodeFromString(SubscriptionResponse.serializer(), body)
            ElevenLabsUsage(
                characterCount = dto.characterCount,
                characterLimit = dto.characterLimit,
                nextResetUnixMs = dto.nextResetUnix?.takeIf { it > 0 }?.let { it * 1000 },
                tier = dto.tier,
            )
        }
    }

    /** Returns a default (every-plan) voice id for the account, or null if none exists. */
    suspend fun defaultVoiceId(apiKey: String): String? = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add an ElevenLabs API key first." }
        val request = Request.Builder()
            .url("$BASE_URL/v2/voices?voice_type=default&page_size=1")
            .header("xi-api-key", apiKey)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(response.code, body))
            json.decodeFromString(VoicesResponse.serializer(), body).voices.firstOrNull()?.voiceId
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), body).detail?.message
        }.getOrNull()
        return when (code) {
            401 -> "ElevenLabs: invalid API key"
            402 -> "ElevenLabs: this voice needs a paid plan — pick a Default voice " +
                "in Text-to-speech settings, or upgrade your ElevenLabs plan."
            429 -> "ElevenLabs: rate limit or character quota exceeded"
            else -> "ElevenLabs error ($code): ${detail ?: "request failed"}"
        }
    }

    companion object {
        private const val BASE_URL = "https://api.elevenlabs.io"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

// "default" + "personal" voices are usable on every plan; "community" (Voice
// Library) voices require a paid plan. Rank controls the order in the picker.
private fun voiceTypeRank(voiceType: String?): Int = when (voiceType) {
    "default" -> 0
    "personal" -> 1
    "workspace" -> 2
    "community" -> 3
    else -> 4
}

private fun voiceTypeLabel(voiceType: String?): String? = when (voiceType) {
    "default" -> "Default voice"
    "personal" -> "Your cloned voice"
    "workspace" -> "Workspace voice"
    "community" -> "Voice Library · paid plans only"
    else -> null
}

@Serializable
private data class VoicesResponse(val voices: List<ApiVoice> = emptyList())

@Serializable
private data class ApiVoice(
    @SerialName("voice_id") val voiceId: String,
    val name: String,
    val category: String? = null,
    @SerialName("voice_type") val voiceType: String? = null,
)

@Serializable
private data class SynthesisRequest(
    val text: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("voice_settings") val voiceSettings: VoiceSettings,
)

@Serializable
private data class VoiceSettings(
    val stability: Float = 0.5f,
    @SerialName("similarity_boost") val similarityBoost: Float = 0.75f,
    val speed: Float = 1.0f,
)

@Serializable
private data class SubscriptionResponse(
    @SerialName("character_count") val characterCount: Int = 0,
    @SerialName("character_limit") val characterLimit: Int = 0,
    @SerialName("next_character_count_reset_unix") val nextResetUnix: Long? = null,
    val tier: String? = null,
)

@Serializable
private data class ErrorResponse(val detail: ErrorDetail? = null)

@Serializable
private data class ErrorDetail(val message: String? = null, val status: String? = null)
