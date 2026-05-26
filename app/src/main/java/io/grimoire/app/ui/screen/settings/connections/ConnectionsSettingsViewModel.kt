package io.grimoire.app.ui.screen.settings.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.auth.github.GitHubAuthRepository
import io.grimoire.app.auth.github.GitHubAuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One row's worth of state on the Connections subpage. [statusLabel] is the
 * human-readable summary shown in the row's subtitle, and [isConnected] lets
 * the UI accent the row when an account is linked.
 */
data class ConnectionStatus(
    val statusLabel: String,
    val isConnected: Boolean,
)

@HiltViewModel
class ConnectionsSettingsViewModel @Inject constructor(
    githubAuthRepository: GitHubAuthRepository,
) : ViewModel() {

    val github: StateFlow<ConnectionStatus> = githubAuthRepository.state
        .map(::toStatus)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            toStatus(githubAuthRepository.state.value),
        )

    private fun toStatus(state: GitHubAuthState): ConnectionStatus = when (state) {
        GitHubAuthState.Disconnected -> ConnectionStatus("Not connected", false)
        is GitHubAuthState.AwaitingUser -> ConnectionStatus("Signing in…", false)
        is GitHubAuthState.Connected -> ConnectionStatus("@${state.login}", true)
        is GitHubAuthState.Failed -> ConnectionStatus("Connection error", false)
    }
}
