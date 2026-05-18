package io.grimoire.app.ui.screen.novelupdates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuBrowseSort
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NuBrowserVM"

/**
 * The four NovelUpdates browse pages. POPULAR/LATEST/LEADERBOARD are plain
 * listings (a fixed sort, no controls); FILTER is the extension-style page
 * with search + ordering + genre/language applied via a sheet.
 */
enum class NuBrowseMode { POPULAR, LATEST, LEADERBOARD, FILTER }

@HiltViewModel
class NovelUpdatesBrowserViewModel @Inject constructor(
    private val repository: NovelUpdatesInfoRepository,
) : ViewModel() {

    private val _results = MutableStateFlow<List<NuSearchResult>>(emptyList())
    val results: StateFlow<List<NuSearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _mode = MutableStateFlow(NuBrowseMode.POPULAR)
    val mode: StateFlow<NuBrowseMode> = _mode.asStateFlow()

    // FILTER-page state. `query` is the live text field; sort/genre/language
    // are the *applied* values that actually drive the request.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(NuBrowseSort.POPULAR)
    val sort: StateFlow<NuBrowseSort> = _sort.asStateFlow()

    private val _genre = MutableStateFlow<String?>(null)
    val genre: StateFlow<String?> = _genre.asStateFlow()

    private val _language = MutableStateFlow<String?>(null)
    val language: StateFlow<String?> = _language.asStateFlow()

    private var page = 1

    init {
        load(reset = true)
    }

    /** Switch top-level page. The plain pages load immediately. */
    fun setMode(newMode: NuBrowseMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        load(reset = true)
    }

    fun setQuery(q: String) { _query.value = q }

    /** Submit the search box on the FILTER page. */
    fun submitSearch() {
        _mode.value = NuBrowseMode.FILTER
        load(reset = true)
    }

    /** Apply the filter sheet's ordering/genre/language and reload. */
    fun applyFilters(sort: NuBrowseSort, genre: String?, language: String?) {
        _sort.value = sort
        _genre.value = genre
        _language.value = language
        _mode.value = NuBrowseMode.FILTER
        load(reset = true)
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
    }

    private fun filterFor(mode: NuBrowseMode): NuBrowseFilter = when (mode) {
        NuBrowseMode.POPULAR -> NuBrowseFilter(sort = NuBrowseSort.POPULAR)
        NuBrowseMode.LATEST -> NuBrowseFilter(sort = NuBrowseSort.LATEST)
        NuBrowseMode.LEADERBOARD -> NuBrowseFilter(sort = NuBrowseSort.RANK)
        NuBrowseMode.FILTER -> NuBrowseFilter(
            query = _query.value.takeIf { it.isNotBlank() },
            sort = _sort.value,
            genreId = _genre.value,
            language = _language.value,
        )
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                page = 1
                _results.value = emptyList()
                _hasMore.value = true
            } else {
                _isLoadingMore.value = true
                page++
            }
            _error.value = null

            runCatching { repository.browse(filterFor(_mode.value), page) }
                .onSuccess { listing ->
                    // De-duplicate by URL across pages so the keyed list never
                    // collides; stop paginating when a page adds nothing new.
                    val merged = (if (reset) listing.results else _results.value + listing.results)
                        .distinctBy { it.url }
                    val grew = merged.size > _results.value.size
                    _results.value = merged
                    _hasMore.value = listing.hasNext && (reset || grew)
                }
                .onFailure { e ->
                    Log.e(TAG, "NU browse failed [mode=${_mode.value} page=$page]", e)
                    _error.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
                    if (!reset) page--
                }

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}
