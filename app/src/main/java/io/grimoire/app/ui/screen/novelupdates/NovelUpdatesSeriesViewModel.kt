package io.grimoire.app.ui.screen.novelupdates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NuSeries
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import io.grimoire.app.extension.repo.ExtensionInstaller
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.app.extension.repo.GitHubRateLimitException
import io.grimoire.app.extension.repo.HashMismatchException
import io.grimoire.app.ui.screen.extensions.InstallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface NuSeriesState {
    data object Loading : NuSeriesState
    data class Loaded(val series: NuSeries) : NuSeriesState
    data class Error(val message: String) : NuSeriesState
}

@HiltViewModel
class NovelUpdatesSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelUpdatesInfoRepository,
    private val extensionRepository: ExtensionRepository,
    private val installer: ExtensionInstaller,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    private val _state = MutableStateFlow<NuSeriesState>(NuSeriesState.Loading)
    val state: StateFlow<NuSeriesState> = _state.asStateFlow()

    /**
     * Installed/available extensions whose declared NovelUpdates groups match
     * one of this series' release groups or English publishers, so the screen
     * can offer to open (or install) the source the series is translated by.
     */
    private val _sourceLinks = MutableStateFlow<List<ExtensionItem>>(emptyList())
    val sourceLinks: StateFlow<List<ExtensionItem>> = _sourceLinks.asStateFlow()

    /** Per-package install progress/errors, keyed by package name. */
    private val _installStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallState>> = _installStates.asStateFlow()

    /** Non-null when an APK is ready to be handed to the system installer. */
    private val _pendingInstall = MutableStateFlow<File?>(null)
    val pendingInstall: StateFlow<File?> = _pendingInstall.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = NuSeriesState.Loading
            runCatching { repository.series(slug) }
                .onSuccess {
                    _state.value = NuSeriesState.Loaded(it)
                    refreshSourceLinks(it)
                }
                .onFailure {
                    _state.value = NuSeriesState.Error(
                        "${it::class.simpleName}: ${it.message ?: "(no message)"}",
                    )
                }
        }
    }

    private fun refreshSourceLinks(series: NuSeries) {
        val groups = (series.releases.map { it.group } + series.englishPublishers).toSet()
        viewModelScope.launch {
            runCatching { extensionRepository.extensionsForNovelUpdatesGroups(groups) }
                .onSuccess { _sourceLinks.value = it }
        }
    }

    fun install(item: ExtensionItem.Available) {
        val pkg = item.packageName
        viewModelScope.launch {
            _installStates.update { it + (pkg to InstallState.Downloading(0L, 0L)) }
            installer.download(item.remote.url, pkg, item.remote.sha256) { read, total ->
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
                        is GitHubRateLimitException -> "GitHub rate limit reached"
                        else -> e.message ?: "Download failed"
                    }
                    _installStates.update { it + (pkg to InstallState.Error(msg)) }
                }
        }
    }

    /** Called by the screen immediately after launching the install intent. */
    fun consumePendingInstall() {
        _pendingInstall.value = null
    }

    /**
     * Called after the system install dialog finishes; re-resolves the links so
     * a freshly-installed source flips from "Install" to "Open".
     */
    fun onInstallResult() {
        (state.value as? NuSeriesState.Loaded)?.let { refreshSourceLinks(it.series) }
    }

    fun dismissInstallError(pkg: String) {
        _installStates.update { it - pkg }
    }
}
