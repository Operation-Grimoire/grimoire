package io.grimoire.app.ui.screen.novelupdates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuBrowseSort
import io.grimoire.app.data.novelupdates.NuListingFilter
import io.grimoire.app.data.novelupdates.NuRankingType
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NuBrowserVM"

/** The three NovelUpdates listing surfaces. */
enum class NuBrowseMode { RANKINGS, LATEST, SEARCH }

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

    private val _mode = MutableStateFlow(NuBrowseMode.RANKINGS)
    val mode: StateFlow<NuBrowseMode> = _mode.asStateFlow()

    private val _rankingType = MutableStateFlow(NuRankingType.POPULAR_ALL)
    val rankingType: StateFlow<NuRankingType> = _rankingType.asStateFlow()

    // Applied filters shared by Rankings + Latest.
    private val _listingFilter = MutableStateFlow(NuListingFilter())
    val listingFilter: StateFlow<NuListingFilter> = _listingFilter.asStateFlow()

    // Applied Series Finder request (Search page). `query` is the live field.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchFilter = MutableStateFlow(NuBrowseFilter())
    val searchFilter: StateFlow<NuBrowseFilter> = _searchFilter.asStateFlow()

    private var page = 1

    init {
        load(reset = true)
    }

    fun setMode(newMode: NuBrowseMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        load(reset = true)
    }

    fun setRankingType(type: NuRankingType) {
        if (_rankingType.value == type) return
        _rankingType.value = type
        if (_mode.value == NuBrowseMode.RANKINGS) load(reset = true)
    }

    /** Apply the shared Rankings/Latest filter sheet. */
    fun applyListingFilter(filter: NuListingFilter) {
        _listingFilter.value = filter
        load(reset = true)
    }

    fun setQuery(q: String) { _query.value = q }

    fun submitSearch() {
        _mode.value = NuBrowseMode.SEARCH
        _searchFilter.value = _searchFilter.value.copy(
            query = _query.value.takeIf { it.isNotBlank() },
        )
        load(reset = true)
    }

    /** Apply the Series Finder filter sheet (sort + genres + languages). */
    fun applySearchFilter(filter: NuBrowseFilter) {
        _mode.value = NuBrowseMode.SEARCH
        _searchFilter.value = filter.copy(query = _query.value.takeIf { it.isNotBlank() })
        load(reset = true)
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
    }

    private suspend fun fetch(page: Int) = when (_mode.value) {
        NuBrowseMode.RANKINGS ->
            repository.ranking(_rankingType.value, _listingFilter.value, page)
        NuBrowseMode.LATEST ->
            repository.latest(_listingFilter.value, page)
        NuBrowseMode.SEARCH ->
            repository.finder(
                _searchFilter.value.copy(
                    query = _query.value.takeIf { it.isNotBlank() },
                ),
                page,
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

            runCatching { fetch(page) }
                .onSuccess { listing ->
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
