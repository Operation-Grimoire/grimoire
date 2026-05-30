package io.grimoire.app.ui.screen.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.auth.github.GitHubAuthStore
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionInstaller
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.app.extension.repo.GitHubRateLimitException
import io.grimoire.app.extension.repo.HashMismatchException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class InstallState {
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : InstallState()
    data class Error(val message: String) : InstallState()
}

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    private val installer: ExtensionInstaller,
    githubAuthStore: GitHubAuthStore,
) : ViewModel() {

    val items: StateFlow<List<ExtensionItem>> = repository.items
    val isFetching: StateFlow<Boolean> = repository.isFetching
    val fetchError: StateFlow<String?> = repository.fetchError
    val authRequiredRepos: StateFlow<List<RepoEntity>> = repository.authRequiredRepos

    /** Login of the currently-connected GitHub account, or null if disconnected. */
    val githubLogin: StateFlow<String?> = githubAuthStore.account
        .map { it?.login }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            githubAuthStore.account.value?.login,
        )

    val repos: StateFlow<List<RepoEntity>> = repository.reposFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallState>> = _installStates.asStateFlow()

    /** Non-null when an APK is ready to be installed. Screen consumes this and clears it. */
    private val _pendingInstall = MutableStateFlow<File?>(null)
    val pendingInstall: StateFlow<File?> = _pendingInstall.asStateFlow()

    /**
     * True when a GitHub 403 rate limit was hit (during refresh or a download).
     * The screen shows a prompt to connect GitHub / try later; dismissing it
     * clears this until the next time the limit is hit.
     */
    private val _rateLimitPrompt = MutableStateFlow(false)
    val rateLimitPrompt: StateFlow<Boolean> = _rateLimitPrompt.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.rateLimited.collect { if (it) _rateLimitPrompt.value = true }
        }
    }

    fun dismissRateLimitPrompt() {
        _rateLimitPrompt.value = false
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun install(item: ExtensionItem.Available) =
        doInstall(item.packageName, item.remote.url, item.remote.sha256)

    fun update(item: ExtensionItem.Installed) =
        doInstall(item.packageName, item.apkUrl, item.remote.sha256)

    private fun doInstall(pkg: String, apkUrl: String, expectedSha256: String?) {
        viewModelScope.launch {
            _installStates.update { it + (pkg to InstallState.Downloading(0L, 0L)) }
            installer.download(apkUrl, pkg, expectedSha256) { read, total ->
                _installStates.update { it + (pkg to InstallState.Downloading(read, total)) }
            }
                .onSuccess { file ->
                    _installStates.update { it - pkg }
                    _pendingInstall.value = file
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is HashMismatchException ->
                            "Download verification failed — try again or switch networks"
                        is GitHubRateLimitException -> {
                            _rateLimitPrompt.value = true
                            "GitHub rate limit reached"
                        }
                        else -> e.message ?: "Download failed"
                    }
                    _installStates.update { it + (pkg to InstallState.Error(msg)) }
                }
        }
    }

    /** Called by the Screen immediately after launching the install intent. */
    fun consumePendingInstall() {
        _pendingInstall.value = null
    }

    /** Called by the Screen's ActivityResult callback after the system install dialog finishes. */
    fun onInstallResult() {
        viewModelScope.launch { repository.refresh() }
    }

    fun dismissInstallError(pkg: String) {
        _installStates.update { it - pkg }
    }

    fun addRepo(name: String, url: String) {
        viewModelScope.launch {
            repository.addRepo(name.trim(), url.trim())
            repository.refresh()
        }
    }

    fun updateRepo(repo: RepoEntity, name: String, url: String) {
        viewModelScope.launch {
            repository.updateRepo(repo.copy(name = name.trim(), indexUrl = url.trim()))
            repository.refresh()
        }
    }

    fun toggleRepo(repo: RepoEntity) {
        viewModelScope.launch {
            repository.updateRepo(repo.copy(enabled = !repo.enabled))
            repository.refresh()
        }
    }

    fun deleteRepo(repo: RepoEntity) {
        viewModelScope.launch {
            repository.deleteRepo(repo)
            repository.refresh()
        }
    }
}
