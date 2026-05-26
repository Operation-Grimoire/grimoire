package io.grimoire.app.auth.github

import io.grimoire.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the connect/disconnect flow. The UI talks to this; the
 * [GitHubAuthInterceptor] talks to [GitHubAuthStore] directly for the hot path.
 *
 * State machine:
 *   Disconnected --connect()-->     AwaitingUser
 *   AwaitingUser  --poll succeeds--> Connected
 *   AwaitingUser  --poll fails-->    Failed (then back to Disconnected/Connected)
 *   AwaitingUser  --cancel()-->      Disconnected
 *   Connected     --disconnect()-->  Disconnected
 *
 * Token revocation by the user at github.com lands as a 401 inside the
 * interceptor, which clears the store; the [account] flow then drives this
 * back to Disconnected on next emission.
 */
@Singleton
class GitHubAuthRepository @Inject constructor(
    private val store: GitHubAuthStore,
    private val deviceAuth: GitHubDeviceAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _transient = MutableStateFlow<TransientState>(TransientState.Idle)
    private var connectJob: Job? = null

    val state: StateFlow<GitHubAuthState> =
        combine(store.account, _transient) { account, transient ->
            when {
                transient is TransientState.Awaiting -> GitHubAuthState.AwaitingUser(transient.challenge)
                transient is TransientState.Failed -> GitHubAuthState.Failed(transient.reason)
                account != null -> GitHubAuthState.Connected(account.login)
                else -> GitHubAuthState.Disconnected
            }
        }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, GitHubAuthState.Disconnected)

    fun isClientConfigured(): Boolean = BuildConfig.GITHUB_OAUTH_CLIENT_ID.isNotBlank()

    fun connect() {
        if (connectJob?.isActive == true) return
        if (!isClientConfigured()) {
            _transient.value = TransientState.Failed(
                AuthFailure.Unexpected("GitHub OAuth client ID is not configured in this build."),
            )
            return
        }
        connectJob = scope.launch { runConnect() }
    }

    fun cancel() {
        connectJob?.cancel()
        connectJob = null
        if (_transient.value is TransientState.Awaiting) {
            _transient.value = TransientState.Idle
        }
    }

    fun disconnect() {
        cancel()
        store.clear()
    }

    fun dismissError() {
        if (_transient.value is TransientState.Failed) _transient.value = TransientState.Idle
    }

    private suspend fun runConnect() {
        val clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID
        val challengeResult = deviceAuth.requestDeviceCode(clientId)
        challengeResult.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            _transient.value = TransientState.Failed(AuthFailure.Network(it.message ?: "request failed"))
            return
        }
        val challenge = challengeResult.getOrThrow()
        _transient.value = TransientState.Awaiting(challenge)

        val tokenResult = deviceAuth.pollForToken(clientId, challenge)
        tokenResult.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            val failure = (it as? DeviceFlowException)?.failure
                ?: AuthFailure.Network(it.message ?: "poll failed")
            _transient.value = TransientState.Failed(failure)
            return
        }
        val token = tokenResult.getOrThrow()

        val loginResult = deviceAuth.fetchUserLogin(token)
        loginResult.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            _transient.value = TransientState.Failed(
                AuthFailure.Network(it.message ?: "could not resolve user"),
            )
            return
        }
        store.save(
            GitHubAccount(
                accessToken = token,
                login = loginResult.getOrThrow(),
                connectedAtMillis = System.currentTimeMillis(),
            ),
        )
        _transient.value = TransientState.Idle
    }

    private sealed class TransientState {
        object Idle : TransientState()
        data class Awaiting(val challenge: DeviceCodeChallenge) : TransientState()
        data class Failed(val reason: AuthFailure) : TransientState()
    }
}
