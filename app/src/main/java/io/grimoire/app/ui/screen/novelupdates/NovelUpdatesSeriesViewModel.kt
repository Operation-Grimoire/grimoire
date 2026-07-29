package io.grimoire.app.ui.screen.novelupdates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.R
import io.grimoire.app.data.local.dao.NuBookmarkDao
import io.grimoire.app.data.local.entity.NuBookmarkEntity
import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
import io.grimoire.app.data.novelupdates.NuSeries
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import io.grimoire.app.extension.repo.ExtensionInstaller
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.app.extension.repo.GitHubRateLimitException
import io.grimoire.app.extension.repo.HashMismatchException
import io.grimoire.app.ui.screen.extensions.InstallState
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: NovelUpdatesInfoRepository,
    private val extensionRepository: ExtensionRepository,
    private val installer: ExtensionInstaller,
    private val bookmarkDao: NuBookmarkDao,
) : ViewModel() {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    private val localizedContext = AppLocale.wrap(context)

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    val isBookmarked: StateFlow<Boolean> = bookmarkDao.isBookmarked(slug)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    /** Save or remove this series from NovelUpdates bookmarks. */
    fun toggleBookmark() {
        val series = (state.value as? NuSeriesState.Loaded)?.series ?: return
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkDao.delete(slug)
            } else {
                bookmarkDao.upsert(
                    NuBookmarkEntity(
                        slug = slug,
                        url = series.url,
                        title = series.title,
                        coverUrl = series.coverUrl,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

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
        // Match only against the actual release groups — by both the stable URL
        // slug (the precise key) and the displayed group name. Deliberately NOT
        // the English publisher: that's the licensor (e.g. Webnovel on a Qidian
        // title), not who's releasing the tracked translation, so folding it in
        // surfaced the publisher instead of the real fan-translation sources.
        val groups = buildSet {
            series.releases.forEach {
                add(it.group)
                NovelUpdatesEndpoints.groupSlugFromUrl(it.groupUrl)?.let(::add)
            }
        }
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
                        is GitHubRateLimitException ->
                            localizedContext.getString(R.string.extensions_rate_limit_title)
                        else -> e.message
                            ?: localizedContext.getString(R.string.error_download_failed)
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
