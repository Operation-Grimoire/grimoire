package io.grimoire.app.auth.athenaeum

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the pair/unpair flow. The pairing screen observes [state]; the
 * [AthenaeumAuthInterceptor] reads the store directly for the hot path. Mirrors
 * [io.grimoire.app.auth.github.GitHubAuthRepository].
 *
 *   Disconnected --pair()-->        AwaitingUser
 *   AwaitingUser --poll succeeds-->  Connected
 *   AwaitingUser --poll fails-->     Failed
 *   Connected    --unpair()-->       Disconnected (also on a 401 clearing the store)
 */
@Singleton
class AthenaeumAuthRepository @Inject constructor(
    private val store: AthenaeumAuthStore,
    private val deviceAuth: AthenaeumDeviceAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transient = MutableStateFlow<Transient>(Transient.Idle)
    private var pairJob: Job? = null

    val state: StateFlow<AthenaeumAuthState> =
        combine(store.account, transient) { account, t ->
            when {
                t is Transient.Awaiting -> AthenaeumAuthState.AwaitingUser(t.challenge)
                t is Transient.Failed -> AthenaeumAuthState.Failed(t.reason)
                account != null -> AthenaeumAuthState.Connected(account.scopes)
                else -> AthenaeumAuthState.Disconnected
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            store.account.value?.let { AthenaeumAuthState.Connected(it.scopes) } ?: AthenaeumAuthState.Disconnected,
        )

    /** Begin pairing, requesting [scopes]. Idempotent while already in flight. */
    fun pair(scopes: Set<String>) {
        if (pairJob?.isActive == true) return
        transient.value = Transient.Idle
        pairJob = scope.launch {
            val challenge = deviceAuth.requestDeviceCode(scopes).getOrElse {
                transient.value = Transient.Failed(AuthFailure.Network(it.message ?: "could not start pairing"))
                return@launch
            }
            transient.value = Transient.Awaiting(challenge)
            deviceAuth.pollForToken(challenge)
                .onSuccess {
                    store.save(it)
                    transient.value = Transient.Idle
                }
                .onFailure {
                    transient.value = Transient.Failed(
                        (it as? DeviceAuthException)?.failure ?: AuthFailure.Network(it.message ?: "pairing failed"),
                    )
                }
        }
    }

    fun cancel() {
        pairJob?.cancel()
        pairJob = null
        transient.value = Transient.Idle
    }

    fun unpair() {
        cancel()
        store.clear()
    }

    private sealed interface Transient {
        data object Idle : Transient
        data class Awaiting(val challenge: DeviceCodeChallenge) : Transient
        data class Failed(val reason: AuthFailure) : Transient
    }
}
