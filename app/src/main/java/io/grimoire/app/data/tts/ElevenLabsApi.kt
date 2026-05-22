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

    /** Lists the voices available to the account behind [apiKey]. */
    suspend fun listVoices(apiKey: String): List<TtsVoice> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add an ElevenLabs API key first." }
        val request = Request.Builder()
            .url("$BASE_URL/voices")
            .header("xi-api-key", apiKey)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(response.code, body))
            json.decodeFromString(VoicesResponse.serializer(), body).voices
                // Premade voices work on every plan; list them first.
                .sortedBy { if (it.category == "premade") 0 else 1 }
                .map { v ->
                    TtsVoice(
                        id = v.voiceId,
                        displayName = v.name,
                        detail = v.category?.replaceFirstChar { it.uppercase() },
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
            .url("$BASE_URL/text-to-speech/$voiceId")
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
            .url("$BASE_URL/user/subscription")
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

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), body).detail?.message
        }.getOrNull()
        return when (code) {
            401 -> "ElevenLabs: invalid API key"
            402 -> "ElevenLabs: this voice isn't available on your plan — pick a " +
                "Premade voice in Text-to-speech settings, or upgrade your plan."
            429 -> "ElevenLabs: rate limit or character quota exceeded"
            else -> "ElevenLabs error ($code): ${detail ?: "request failed"}"
        }
    }

    companion object {
        private const val BASE_URL = "https://api.elevenlabs.io/v1"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

@Serializable
private data class VoicesResponse(val voices: List<ApiVoice> = emptyList())

@Serializable
private data class ApiVoice(
    @SerialName("voice_id") val voiceId: String,
    val name: String,
    val category: String? = null,
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
