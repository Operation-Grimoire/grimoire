package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Filter
import io.grimoire.api.model.Novel
import io.grimoire.api.source.CatalogueSource
import io.grimoire.api.source.ConfigurableSource
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
import kotlinx.coroutines.flow.filter
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

    val libraryUrls: StateFlow<Set<String>> = novelDao.getFavoriteUrls()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val displayMode: StateFlow<BrowseDisplayMode> = browsePreferences.displayMode.stateIn(viewModelScope)
    val gridColumns: StateFlow<Int> = browsePreferences.gridColumns.stateIn(viewModelScope)

    val packageName: String = checkNotNull(savedStateHandle["pkg"])

    private val loaded get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == packageName }

    private val source: CatalogueSource? get() = loaded?.source as? CatalogueSource

    val isConfigurable: Boolean get() = loaded?.source is ConfigurableSource

    val sourceName: String get() = loaded?.info?.label?.substringAfter(": ", loaded?.info?.label.orEmpty()).orEmpty()
        .ifEmpty { packageName }

    val sourceBaseUrl: String get() = loaded?.source?.javaClass
        ?.getAnnotation(SourceInfo::class.java)?.baseUrl ?: ""

    val supportsSearchWithFilters: Boolean get() = source?.supportsSearchWithFilters ?: false

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

    private val _mode = MutableStateFlow(BrowseMode.POPULAR)
    val mode: StateFlow<BrowseMode> = _mode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _activeFilters = MutableStateFlow<List<Filter<*>>>(emptyList())
    val activeFilters: StateFlow<List<Filter<*>>> = _activeFilters.asStateFlow()

    private var page = 1

    init {
        val initialQuery: String? = savedStateHandle["q"]
        if (!initialQuery.isNullOrBlank()) {
            _query.value = initialQuery
            _mode.value = BrowseMode.SEARCH
        }
        // Wait for ExtensionManager scan to finish before loading.
        // StateFlow emits current value immediately, so if scan already completed this is instant.
        viewModelScope.launch {
            extensionManager.extensions
                .filter { list -> list.any { it.info.packageName == packageName } }
                .take(1)
                .collect {
                    initFilters()
                    load(reset = true)
                }
        }
    }

    private fun initFilters() {
        val src = source ?: return
        val list = src.getFilterList()
        _filters.value = list
        _filterLoadState.value = when {
            list.isEmpty() -> FilterLoadState.None
            src.hasDynamicFilters -> FilterLoadState.NeedsLoad
            else -> FilterLoadState.Ready
        }
    }

    fun loadFilterOptions() {
        val src = source ?: return
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
        if (newMode != BrowseMode.SEARCH) _query.value = ""
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

    fun setDisplayMode(mode: BrowseDisplayMode) = viewModelScope.launch {
        browsePreferences.displayMode.set(mode)
    }

    fun setGridColumns(count: Int) = viewModelScope.launch {
        browsePreferences.gridColumns.set(count.coerceIn(2, 5))
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val src = source ?: run { _error.value = "Source not available"; return }
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

            runCatching {
                val result = when (_mode.value) {
                    BrowseMode.POPULAR -> src.getPopularNovels(page)
                    BrowseMode.LATEST -> src.getLatestUpdates(page)
                    BrowseMode.SEARCH -> src.searchNovels(_query.value, page, _activeFilters.value)
                }
                if (reset) _novels.value = result
                else _novels.value = (_novels.value + result).distinctBy { it.url }
                _hasMore.value = result.isNotEmpty()
            }.onFailure { e ->
                Log.e(TAG, "Load failed [mode=${_mode.value} page=$page pkg=$packageName]", e)
                _error.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
                if (!reset) page--
            }

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}
