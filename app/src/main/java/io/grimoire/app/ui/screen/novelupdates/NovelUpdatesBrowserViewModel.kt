package io.grimoire.app.ui.screen.novelupdates

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
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

/** The two NovelUpdates browse surfaces (Search has its own screen). */
enum class NuBrowseMode { RANKINGS, LATEST }

@HiltViewModel
class NovelUpdatesBrowserViewModel @Inject constructor(
    private val repository: NovelUpdatesInfoRepository,
    savedStateHandle: SavedStateHandle,
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

    private val _mode = MutableStateFlow(
        savedStateHandle.get<String>("mode")
            ?.let { runCatching { NuBrowseMode.valueOf(it) }.getOrNull() }
            ?: NuBrowseMode.RANKINGS,
    )
    val mode: StateFlow<NuBrowseMode> = _mode.asStateFlow()

    private val _rankingType = MutableStateFlow(NuRankingType.POPULAR_ALL)
    val rankingType: StateFlow<NuRankingType> = _rankingType.asStateFlow()

    private val _filter = MutableStateFlow(NuListingFilter())
    val filter: StateFlow<NuListingFilter> = _filter.asStateFlow()

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
    fun applyFilter(filter: NuListingFilter) {
        _filter.value = filter
        load(reset = true)
    }

    /** The live NU page URL for the current mode/filter (for "open in WebView"). */
    fun currentPageUrl(): String = when (_mode.value) {
        NuBrowseMode.RANKINGS ->
            NovelUpdatesEndpoints.seriesRankingUrl(_rankingType.value, _filter.value, 1)
        NuBrowseMode.LATEST ->
            NovelUpdatesEndpoints.latestSeriesUrl(_filter.value, 1)
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
    }

    private suspend fun fetch(page: Int) = when (_mode.value) {
        NuBrowseMode.RANKINGS -> repository.ranking(_rankingType.value, _filter.value, page)
        NuBrowseMode.LATEST -> repository.latest(_filter.value, page)
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
