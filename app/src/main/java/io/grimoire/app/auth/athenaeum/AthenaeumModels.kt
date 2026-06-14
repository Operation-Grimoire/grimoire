package io.grimoire.app.auth.athenaeum

/**
 * Challenge from POST /device/code: the user types [userCode] at
 * [verificationUri] while the app polls /device/token with [deviceCode].
 */
data class DeviceCodeChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val interval: Int,
    val expiresInSeconds: Int,
)

/** The opaque device token + granted scopes we persist once paired. */
data class AthenaeumAccount(
    val token: String,
    val scopes: Set<String>,
    val connectedAtMillis: Long,
    /** Account identity from GET /device/me; null until fetched (or if it fails). */
    val email: String? = null,
    val username: String? = null,
)

/** Identity shared by the backend over device auth (GET /device/me). */
data class AthenaeumIdentity(
    val email: String?,
    val username: String?,
)

/** Surface state for the pairing screen. */
sealed class AthenaeumAuthState {
    data object Disconnected : AthenaeumAuthState()

    /** Device-flow handshake in progress; UI shows [challenge.userCode] + verification URL. */
    data class AwaitingUser(val challenge: DeviceCodeChallenge) : AthenaeumAuthState()

    data class Connected(
        val scopes: Set<String>,
        val email: String? = null,
        val username: String? = null,
    ) : AthenaeumAuthState()

    data class Failed(val reason: AuthFailure) : AthenaeumAuthState()
}

/** Reasons the device-flow attempt can fail or be interrupted. */
sealed class AuthFailure(val message: String) {
    data object AccessDenied : AuthFailure("Authorization was denied")
    data object Expired : AuthFailure("Code expired before authorization completed")
    data object Cancelled : AuthFailure("Cancelled")
    data class Network(val cause: String) : AuthFailure("Network error: $cause")
    data class Unexpected(val cause: String) : AuthFailure("Unexpected error: $cause")
}
