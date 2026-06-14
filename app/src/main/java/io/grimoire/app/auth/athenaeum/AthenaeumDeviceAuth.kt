package io.grimoire.app.auth.athenaeum

import io.grimoire.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Athenaeum device authorization grant client (RFC 8628 over our own endpoints):
 *  1. [requestDeviceCode] → POST /device/code with the scopes we want.
 *  2. [pollForToken] → POST /device/token at the returned interval until the
 *     user approves in a browser; returns the opaque ath_dev_ token + scopes.
 */
@Singleton
class AthenaeumDeviceAuth @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()
    private val base = BuildConfig.ATHENAEUM_API_BASE.trimEnd('/')

    @Serializable private data class CodeRequest(val scopes: List<String>)
    @Serializable private data class CodeResponse(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int,
    )

    @Serializable private data class TokenRequest(val deviceCode: String)
    @Serializable private data class TokenResponse(val token: String, val scopes: List<String> = emptyList())
    @Serializable private data class ErrorResponse(val error: String? = null)
    @Serializable private data class IdentityResponse(
        val email: String? = null,
        val username: String? = null,
    )

    suspend fun requestDeviceCode(scopes: Set<String>): Result<DeviceCodeChallenge> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(CodeRequest(scopes.toList())).toRequestBody(jsonMedia)
                val req = Request.Builder().url("$base/device/code").post(body).build()
                client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} from /device/code" }
                    val r = json.decodeFromString<CodeResponse>(resp.body!!.string())
                    DeviceCodeChallenge(r.deviceCode, r.userCode, r.verificationUri, r.interval, r.expiresIn)
                }
            }
        }

    /**
     * Polls until the token is issued, denied, expired, or cancelled. Transient
     * failures (a network blip, the app backgrounded while the user finishes in
     * the browser, a 5xx) are swallowed so the next interval retries — only the
     * server's terminal `error` field or the deadline tears the flow down. This
     * mirrors [io.grimoire.app.auth.github.GitHubDeviceAuth.pollForToken].
     */
    suspend fun pollForToken(challenge: DeviceCodeChallenge): Result<AthenaeumAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(TokenRequest(challenge.deviceCode)).toRequestBody(jsonMedia)
                val deadline = System.currentTimeMillis() + challenge.expiresInSeconds * 1000L
                while (true) {
                    delay(challenge.interval * 1000L)
                    if (System.currentTimeMillis() > deadline) throw DeviceAuthException(AuthFailure.Expired)
                    val req = Request.Builder().url("$base/device/token").post(body).build()
                    val text = try {
                        client.newCall(req).execute().use { resp ->
                            val t = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) {
                                val r = json.decodeFromString<TokenResponse>(t)
                                return@runCatching AthenaeumAccount(r.token, r.scopes.toSet(), System.currentTimeMillis())
                            }
                            t
                        }
                    } catch (e: java.io.IOException) {
                        // Network blip / app backgrounded — retry next interval.
                        null
                    } ?: continue
                    when (runCatching { json.decodeFromString<ErrorResponse>(text).error }.getOrNull()) {
                        "authorization_pending" -> {} // keep polling
                        "access_denied" -> throw DeviceAuthException(AuthFailure.AccessDenied)
                        "expired_token" -> throw DeviceAuthException(AuthFailure.Expired)
                        // Unknown / unparseable non-2xx: transient, keep polling
                        // (the deadline still bounds the loop).
                        else -> {}
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        }

    /**
     * Fetch the paired account's identity (email + handle) with the device token.
     * Uses GET /device/me — the edge-authorized /me can't serve opaque device
     * tokens. Mirrors [io.grimoire.app.auth.github.GitHubDeviceAuth.fetchUserLogin];
     * failure is non-fatal to pairing, so the caller treats it as best-effort.
     */
    suspend fun fetchIdentity(token: String): Result<AthenaeumIdentity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$base/device/me")
                    .header("Authorization", "Bearer $token")
                    .build()
                client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} from /device/me" }
                    val r = json.decodeFromString<IdentityResponse>(resp.body!!.string())
                    AthenaeumIdentity(r.email, r.username)
                }
            }
        }
}

/** Carries a typed [AuthFailure] out of the polling loop. */
class DeviceAuthException(val failure: AuthFailure) : RuntimeException(failure.message)
