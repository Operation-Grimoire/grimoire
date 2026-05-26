package io.grimoire.app.auth.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub OAuth Device Flow client.
 *
 *  1. [requestDeviceCode] asks GitHub for a [DeviceCodeChallenge] (user code +
 *     URL). UI shows these to the user.
 *  2. [pollForToken] polls the token endpoint at the cadence GitHub returned,
 *     until the user finishes authorizing (or the code expires).
 *  3. [fetchUserLogin] grabs the `login` for display.
 *
 * Docs: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow
 */
@Singleton
class GitHubDeviceAuth @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestDeviceCode(clientId: String): Result<DeviceCodeChallenge> =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("scope", "repo")
                    .build()
                val req = Request.Builder()
                    .url("https://github.com/login/device/code")
                    .header("Accept", "application/json")
                    .post(form)
                    .build()
                client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} from /login/device/code" }
                    val body = resp.body!!.string()
                    val r = json.decodeFromString<DeviceCodeResponse>(body)
                    DeviceCodeChallenge(
                        deviceCode = r.device_code,
                        userCode = r.user_code,
                        verificationUri = r.verification_uri,
                        interval = r.interval,
                        expiresInSeconds = r.expires_in,
                    )
                }
            }
        }

    /**
     * Polls until success, [AuthFailure.AccessDenied], [AuthFailure.Expired],
     * or network failure. Honors the `slow_down` response by extending the poll
     * interval (per spec, by at least 5 seconds).
     */
    suspend fun pollForToken(
        clientId: String,
        challenge: DeviceCodeChallenge,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var intervalSeconds = challenge.interval
            val deadline = System.currentTimeMillis() + challenge.expiresInSeconds * 1_000L
            while (true) {
                delay(intervalSeconds * 1_000L)
                if (System.currentTimeMillis() > deadline) {
                    throw DeviceFlowException(AuthFailure.Expired)
                }
                val form = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", challenge.deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                val req = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(form)
                    .build()
                val body = client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} from token endpoint" }
                    resp.body!!.string()
                }
                val r = json.decodeFromString<TokenResponse>(body)
                if (r.access_token != null) return@runCatching r.access_token
                when (r.error) {
                    "authorization_pending" -> {} // keep polling
                    "slow_down" -> intervalSeconds += 5
                    "expired_token" -> throw DeviceFlowException(AuthFailure.Expired)
                    "access_denied" -> throw DeviceFlowException(AuthFailure.AccessDenied)
                    else -> throw DeviceFlowException(
                        AuthFailure.Unexpected(r.error ?: "unknown response"),
                    )
                }
            }
            @Suppress("UNREACHABLE_CODE") ""
        }
    }

    suspend fun fetchUserLogin(accessToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
                client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} from /user" }
                    val body = resp.body!!.string()
                    json.decodeFromString<UserResponse>(body).login
                }
            }
        }

    @Serializable
    private data class DeviceCodeResponse(
        val device_code: String,
        val user_code: String,
        val verification_uri: String,
        val expires_in: Int,
        val interval: Int = 5,
    )

    @Serializable
    private data class TokenResponse(
        val access_token: String? = null,
        val token_type: String? = null,
        val scope: String? = null,
        val error: String? = null,
        val error_description: String? = null,
    )

    @Serializable
    private data class UserResponse(val login: String)
}

class DeviceFlowException(val failure: AuthFailure) : RuntimeException(failure.message)
