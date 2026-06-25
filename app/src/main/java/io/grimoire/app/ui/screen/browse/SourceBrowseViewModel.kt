package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.filter.Filter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.network.CloudflareException
import io.grimoire.api.source.feature.ConfigurableSource
import io.grimoire.api.source.feature.FilterSource
import io.grimoire.api.source.feature.LatestSource
import io.grimoire.api.source.feature.MultiHostSource
import io.grimoire.api.source.feature.MultiLanguageSource
import io.grimoire.api.source.feature.PopularSource
import io.grimoire.api.source.feature.SearchSource
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.ExtensionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SourceBrowseVM"

enum class BrowseMode { POPULAR, LATEST, SEARCH }

sealed interface FilterLoadState {
    /** Source has no filters at all. */
    data object None : FilterLoadState
    /** Source has only static filters — ready to use immediately. */
    data object Ready : FilterLoadState
    /** Source has dynamic filters that haven't been fetched yet. */
    data object NeedsLoad : FilterLoadState
    data object Loading : FilterLoadState
    data object Loaded : FilterLoadState
    data class Error(val message: String) : FilterLoadState
}

@HiltViewModel
class SourceBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val browsePreferences: BrowsePreferences,
    private val novelDao: NovelDao,
) : ViewModel() {

    val packageName: String = checkNotNull(savedStateHandle["pkg"])

    val libraryUrls: StateFlow<Set<String>> = extensionManager.extensions
        .map { list -> list.firstOrNull { it.info.packageName == packageName }?.id }
        .distinctUntilChanged()
        .flatMapLatest { sourceId ->
            if (sourceId == null) flowOf(emptySet())
            else novelDao.getFavoriteUrlsBySource(sourceId).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val displayMode: StateFlow<BrowseDisplayMode> = browsePreferences.displayMode.stateIn(viewModelScope)
    val gridColumns: StateFlow<Int> = browsePreferences.gridColumns.stateIn(viewModelScope)

    private val loaded get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == packageName }

    // Per-capability views of the loaded source — a source opts into each by
    // declaring the matching interface.
    private val popularSource: PopularSource? get() = loaded?.source as? PopularSource
    private val latestSource: LatestSource? get() = loaded?.source as? LatestSource
    private val searchSource: SearchSource? get() = loaded?.source as? SearchSource
    private val filterSource: FilterSource? get() = loaded?.source as? FilterSource

    val supportsPopular: Boolean get() = loaded?.source is PopularSource
    val supportsLatest: Boolean get() = loaded?.source is LatestSource
    val supportsSearch: Boolean get() = loaded?.source is SearchSource
    val supportsFilters: Boolean get() = loaded?.source is FilterSource

    val isConfigurable: Boolean
        get() = loaded?.source is ConfigurableSource ||
            loaded?.source is MultiLanguageSource ||
            loaded?.source is MultiHostSource

    val sourceName: String get() = loaded?.info?.label?.substringAfter(": ", loaded?.info?.label.orEmpty()).orEmpty()
        .ifEmpty { packageName }

    val sourceBaseUrl: String get() = loaded?.source?.javaClass
        ?.getAnnotation(SourceInfo::class.java)?.baseUrl ?: ""

    val supportsSearchWithFilters: Boolean get() = searchSource?.supportsSearchWithFilters ?: false

    private val _filters = MutableStateFlow<List<Filter<*>>>(emptyList())
    val filters: StateFlow<List<Filter<*>>> = _filters.asStateFlow()

    private val _filterLoadState = MutableStateFlow<FilterLoadState>(FilterLoadState.None)
    val filterLoadState: StateFlow<FilterLoadState> = _filterLoadState.asStateFlow()

    private val _novels = MutableStateFlow<List<Novel>>(emptyList())
    val novels: StateFlow<List<Novel>> = _novels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * True when the most recent load hit a Cloudflare challenge that the
     * silent interceptor couldn't solve. Surfaced separately from [_error]
     * because the UI shows a dedicated "open in WebView" CTA rather than the
     * generic error message + retry.
     */
    private val _cloudflareBlocked = MutableStateFlow(false)
    val cloudflareBlocked: StateFlow<Boolean> = _cloudflareBlocked.asStateFlow()

    private val _mode = MutableStateFlow(BrowseMode.POPULAR)
    val mode: StateFlow<BrowseMode> = _mode.asStateFlow()

    /**
     * Held in the VM so the grid scroll position survives navigating to a novel
     * and back (the VM outlives the screen on the source-browse back-stack entry).
     * Resetting to the top happens on real mode changes only — see the collector
     * in [init] — so returning from a novel doesn't jump to the top.
     */
    val gridState = LazyGridState()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * True when the screen was opened with a preset search query (e.g. a deep
     * link from the NovelUpdates browser's "Read with"). The screen uses this to
     * start in active-search mode so the query bar is visible instead of the
     * Popular tab.
     */
    val openedWithSearch: Boolean = !savedStateHandle.get<String>("q").isNullOrBlank()

    private val _activeFilters = MutableStateFlow<List<Filter<*>>>(emptyList())
    val activeFilters: StateFlow<List<Filter<*>>> = _activeFilters.asStateFlow()

    private var page = 1

    init {
        val initialQuery: String? = savedStateHandle["q"]
        if (!initialQuery.isNullOrBlank()) {
            _query.value = initialQuery
            _mode.value = BrowseMode.SEARCH
        }
        // Jump to the top only when the user actually switches Popular/Latest/
        // Search — drop(1) skips the initial value so it never fires on screen
        // re-entry (e.g. returning from a novel).
        viewModelScope.launch {
            _mode.drop(1).collect { gridState.scrollToItem(0) }
        }
        // Wait for ExtensionManager scan to finish before loading.
        // StateFlow emits current value immediately, so if scan already completed this is instant.
        viewModelScope.launch {
            extensionManager.extensions
                .filter { list -> list.any { it.info.packageName == packageName } }
                .take(1)
                .collect {
                    // Default to the first capability the source actually offers
                    // (a search-only source opens on Search, not an empty Popular).
                    if (savedStateHandle.get<String>("q").isNullOrBlank()) {
                        _mode.value = defaultMode()
                    }
                    initFilters()
                    load(reset = true)
                }
        }
    }

    private fun initFilters() {
        val src = filterSource ?: return
        val list = src.getFilterList()
        _filters.value = list
        _filterLoadState.value = when {
            list.isEmpty() -> FilterLoadState.None
            src.hasDynamicFilters -> FilterLoadState.NeedsLoad
            else -> FilterLoadState.Ready
        }
    }

    fun loadFilterOptions() {
        val src = filterSource ?: return
        if (!src.hasDynamicFilters) return
        if (_filterLoadState.value is FilterLoadState.Loading) return
        viewModelScope.launch {
            _filterLoadState.value = FilterLoadState.Loading
            runCatching { src.fetchFilterOptions() }
                .onSuccess {
                    _filters.value = it
                    _filterLoadState.value = FilterLoadState.Loaded
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load filter options [pkg=$packageName]", e)
                    _filterLoadState.value = FilterLoadState.Error(
                        e.message ?: e::class.simpleName ?: "Unknown error"
                    )
                }
        }
    }

    fun canApplyFilters(): Boolean = when (_filterLoadState.value) {
        FilterLoadState.None, FilterLoadState.Loading, FilterLoadState.NeedsLoad -> false
        FilterLoadState.Ready, FilterLoadState.Loaded -> true
        is FilterLoadState.Error -> false
    }

    fun setMode(newMode: BrowseMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        // Popular/Latest are unfiltered browses — drop any applied filters so the
        // Filters chip doesn't stay highlighted alongside the active mode.
        if (newMode != BrowseMode.SEARCH) {
            _query.value = ""
            _activeFilters.value = emptyList()
        }
        load(reset = true)
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun submitSearch() {
        if (_query.value.isBlank()) return
        // Plain top-bar search: filters are reset to source defaults so search
        // and filter remain conceptually separate operations.
        _activeFilters.value = emptyList()
        _mode.value = BrowseMode.SEARCH
        load(reset = true)
    }

    /**
     * @param sheetQuery filter sheet's own query field, only honoured when the
     *   source declares [CatalogueSource.supportsSearchWithFilters]. For other
     *   sources the top-bar query is cleared so filter results aren't polluted
     *   by a stale search keyword.
     */
    fun applyFilters(applied: List<Filter<*>>, sheetQuery: String = "") {
        if (!canApplyFilters()) return
        _activeFilters.value = applied
        _query.value = if (supportsSearchWithFilters) sheetQuery else ""
        _mode.value = BrowseMode.SEARCH
        load(reset = true)
    }

    fun setGridColumns(count: Int) = viewModelScope.launch {
        browsePreferences.gridColumns.set(count.coerceIn(2, 5))
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
    }

    private fun defaultMode(): BrowseMode = when {
        supportsPopular -> BrowseMode.POPULAR
        supportsLatest -> BrowseMode.LATEST
        else -> BrowseMode.SEARCH
    }

    /** Source offers only Search — the screen opens straight into the input. */
    val defaultsToSearch: Boolean
        get() = supportsSearch && !supportsPopular && !supportsLatest

    private fun load(reset: Boolean) {
        if (loaded?.source == null) { _error.value = "Source not available"; return }
        // A blank, unfiltered search has nothing to browse: wait for the user to
        // type instead of auto-firing an empty query (which some sources answer
        // with a seeded browse). Clears any prior results and stops loading.
        if (_mode.value == BrowseMode.SEARCH && _query.value.isBlank() && _activeFilters.value.isEmpty()) {
            page = 1
            _novels.value = emptyList()
            _hasMore.value = false
            _isLoading.value = false
            _isLoadingMore.value = false
            _error.value = null
            _cloudflareBlocked.value = false
            return
        }
        viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                page = 1
                _novels.value = emptyList()
                _hasMore.value = true
            } else {
                _isLoadingMore.value = true
                page++
            }
            _error.value = null
            _cloudflareBlocked.value = false

            runCatching {
                val result = when (_mode.value) {
                    BrowseMode.POPULAR -> popularSource?.getPopularNovels(page).orEmpty()
                    BrowseMode.LATEST -> latestSource?.getLatestUpdates(page).orEmpty()
                    BrowseMode.SEARCH ->
                        searchSource?.searchNovels(_query.value, page, _activeFilters.value).orEmpty()
                }
                // Always de-duplicate by URL: a source can legitimately return
                // the same item twice (on one page or across pages), and the
                // keyed list would crash on duplicate keys. Stop paginating
                // when a page adds nothing new (empty, or all duplicates).
                val merged = (if (reset) result else _novels.value + result)
                    .distinctBy { it.url }
                val grew = merged.size > _novels.value.size
                _novels.value = merged
                _hasMore.value = if (reset) result.isNotEmpty() else grew
            }.onFailure { e ->
                Log.e(TAG, "Load failed [mode=${_mode.value} page=$page pkg=$packageName]", e)
                if (e is CloudflareException) {
                    _cloudflareBlocked.value = true
                } else {
                    _error.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
                }
                if (!reset) page--
            }

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}
