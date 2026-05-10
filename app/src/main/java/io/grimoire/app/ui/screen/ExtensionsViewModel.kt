package io.grimoire.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionInstaller
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class InstallState { IDLE, DOWNLOADING, ERROR }

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    private val installer: ExtensionInstaller,
) : ViewModel() {

    val items: StateFlow<List<ExtensionItem>> = repository.items
    val isFetching: StateFlow<Boolean> = repository.isFetching
    val fetchError: StateFlow<String?> = repository.fetchError

    val repos: StateFlow<List<RepoEntity>> = repository.reposFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallState>> = _installStates.asStateFlow()

    /** Non-null when an APK is ready to be installed. Screen consumes this and clears it. */
    private val _pendingInstall = MutableStateFlow<File?>(null)
    val pendingInstall: StateFlow<File?> = _pendingInstall.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun install(item: ExtensionItem.Available) = doInstall(item.packageName, item.remote.url)
    fun update(item: ExtensionItem.Installed) = doInstall(item.packageName, item.apkUrl)

    private fun doInstall(pkg: String, apkUrl: String) {
        viewModelScope.launch {
            _installStates.update { it + (pkg to InstallState.DOWNLOADING) }
            installer.download(apkUrl, pkg)
                .onSuccess { file ->
                    _installStates.update { it - pkg }
                    _pendingInstall.value = file
                }
                .onFailure {
                    _installStates.update { it + (pkg to InstallState.ERROR) }
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

    fun addRepo(name: String, url: String) {
        viewModelScope.launch { repository.addRepo(name.trim(), url.trim()) }
    }

    fun updateRepo(repo: RepoEntity, name: String, url: String) {
        viewModelScope.launch { repository.updateRepo(repo.copy(name = name.trim(), indexUrl = url.trim())) }
    }

    fun toggleRepo(repo: RepoEntity) {
        viewModelScope.launch { repository.updateRepo(repo.copy(enabled = !repo.enabled)) }
    }

    fun deleteRepo(repo: RepoEntity) {
        viewModelScope.launch { repository.deleteRepo(repo) }
    }
}
