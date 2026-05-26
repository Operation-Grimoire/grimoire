package io.grimoire.app.ui.screen.settings.github

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.auth.github.GitHubAuthRepository
import io.grimoire.app.auth.github.GitHubAuthState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GitHubAuthViewModel @Inject constructor(
    private val repository: GitHubAuthRepository,
) : ViewModel() {
    val state: StateFlow<GitHubAuthState> = repository.state
    val isClientConfigured: Boolean = repository.isClientConfigured()

    fun connect() = repository.connect()
    fun cancel() = repository.cancel()
    fun disconnect() = repository.disconnect()
    fun dismissError() = repository.dismissError()
}
