package io.grimoire.app.auth.github

/**
 * Challenge returned by GitHub when starting the OAuth Device Flow.
 *
 * The user types [userCode] into [verificationUri] in a browser; meanwhile the
 * app polls the token endpoint with [deviceCode] every [interval] seconds. The
 * challenge becomes invalid after [expiresInSeconds].
 */
data class DeviceCodeChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val interval: Int,
    val expiresInSeconds: Int,
)

/** Token + minimum profile info we persist for a connected account. */
data class GitHubAccount(
    val accessToken: String,
    val login: String,
    val connectedAtMillis: Long,
)

/** Surface state for the connect screen / smart-prompt callers. */
sealed class GitHubAuthState {
    object Disconnected : GitHubAuthState()

    /** Device-flow handshake in progress; UI shows [challenge.userCode]. */
    data class AwaitingUser(val challenge: DeviceCodeChallenge) : GitHubAuthState()

    data class Connected(val login: String) : GitHubAuthState()

    /** Terminal-for-this-attempt error; Disconnected after the user dismisses. */
    data class Failed(val reason: AuthFailure) : GitHubAuthState()
}

/** Reasons the device-flow attempt can fail or be interrupted. */
sealed class AuthFailure(val message: String) {
    object AccessDenied : AuthFailure("Authorization was denied")
    object Expired : AuthFailure("Code expired before authorization completed")
    object Cancelled : AuthFailure("Cancelled")
    data class Network(val cause: String) : AuthFailure("Network error: $cause")
    data class Unexpected(val cause: String) : AuthFailure("Unexpected error: $cause")
}
