package io.grimoire.app.ui.screen.novelupdates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.data.novelupdates.NuTag
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NuSearchVM"

@HiltViewModel
class NovelUpdatesSearchViewModel @Inject constructor(
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(NuBrowseFilter())
    val filter: StateFlow<NuBrowseFilter> = _filter.asStateFlow()

    // The full NU tag vocabulary (loaded once from the Series Finder page).
    private val _tags = MutableStateFlow<List<NuTag>>(emptyList())
    val tags: StateFlow<List<NuTag>> = _tags.asStateFlow()

    private val _tagsLoading = MutableStateFlow(false)
    val tagsLoading: StateFlow<Boolean> = _tagsLoading.asStateFlow()

    private var page = 1

    init {
        load(reset = true)
        loadTags()
    }

    private fun loadTags() {
        if (_tags.value.isNotEmpty() || _tagsLoading.value) return
        viewModelScope.launch {
            _tagsLoading.value = true
            _tags.value = runCatching { repository.tags() }.getOrDefault(emptyList())
            _tagsLoading.value = false
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun submitSearch() {
        _filter.value = _filter.value.copy(query = _query.value.takeIf { it.isNotBlank() })
        load(reset = true)
    }

    /** Apply the advanced filter sheet (keeps the live query in sync). */
    fun applyFilter(filter: NuBrowseFilter) {
        _filter.value = filter.copy(query = _query.value.takeIf { it.isNotBlank() })
        load(reset = true)
    }

    /** The live Series Finder URL for the current query/filter. */
    fun currentPageUrl(): String = NovelUpdatesEndpoints.seriesFinderUrl(
        _filter.value.copy(query = _query.value.takeIf { it.isNotBlank() }),
        1,
    )

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
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

            val request = _filter.value.copy(query = _query.value.takeIf { it.isNotBlank() })
            runCatching { repository.finder(request, page) }
                .onSuccess { listing ->
                    val merged = (if (reset) listing.results else _results.value + listing.results)
                        .distinctBy { it.url }
                    val grew = merged.size > _results.value.size
                    _results.value = merged
                    _hasMore.value = listing.hasNext && (reset || grew)
                }
                .onFailure { e ->
                    Log.e(TAG, "NU search failed [page=$page]", e)
                    _error.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
                    if (!reset) page--
                }

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}
