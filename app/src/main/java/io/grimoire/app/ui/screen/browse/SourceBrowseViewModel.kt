package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Filter
import io.grimoire.api.model.Novel
import io.grimoire.api.source.CatalogueSource
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.ExtensionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SourceBrowseVM"

enum class BrowseMode { POPULAR, LATEST, SEARCH }

@HiltViewModel
class SourceBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val browsePreferences: BrowsePreferences,
) : ViewModel() {

    val displayMode: StateFlow<BrowseDisplayMode> = browsePreferences.displayMode.stateIn(viewModelScope)
    val gridColumns: StateFlow<Int> = browsePreferences.gridColumns.stateIn(viewModelScope)

    val packageName: String = checkNotNull(savedStateHandle["pkg"])

    private val loaded get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == packageName }

    private val source: CatalogueSource? get() = loaded?.source as? CatalogueSource

    val sourceName: String get() = loaded?.info?.label?.substringAfter(": ", loaded?.info?.label.orEmpty()).orEmpty()
        .ifEmpty { packageName }

    val filters: List<Filter<*>> get() = source?.getFilterList() ?: emptyList()

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
        // Wait for ExtensionManager scan to finish before loading.
        // StateFlow emits current value immediately, so if scan already completed this is instant.
        viewModelScope.launch {
            extensionManager.extensions
                .filter { list -> list.any { it.info.packageName == packageName } }
                .take(1)
                .collect { load(reset = true) }
        }
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
        _mode.value = BrowseMode.SEARCH
        load(reset = true)
    }

    fun applyFilters(applied: List<Filter<*>>) {
        _activeFilters.value = applied
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
