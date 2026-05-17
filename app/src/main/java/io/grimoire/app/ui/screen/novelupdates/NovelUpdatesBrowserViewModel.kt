package io.grimoire.app.ui.screen.novelupdates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuBrowseSort
import io.grimoire.app.data.novelupdates.NuRankWindow
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NuBrowserVM"

enum class NuBrowseMode { LATEST, POPULAR, LEADERBOARD, SEARCH }

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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _genre = MutableStateFlow<String?>(null)
    val genre: StateFlow<String?> = _genre.asStateFlow()

    private val _language = MutableStateFlow<String?>(null)
    val language: StateFlow<String?> = _language.asStateFlow()

    private val _rankWindow = MutableStateFlow(NuRankWindow.WEEK)
    val rankWindow: StateFlow<NuRankWindow> = _rankWindow.asStateFlow()

    private var page = 1

    init {
        load(reset = true)
    }

    fun setMode(newMode: NuBrowseMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        if (newMode != NuBrowseMode.SEARCH) _query.value = ""
        load(reset = true)
    }

    fun setQuery(q: String) { _query.value = q }

    fun submitSearch() {
        if (_query.value.isBlank()) return
        _mode.value = NuBrowseMode.SEARCH
        load(reset = true)
    }

    fun setGenre(slug: String?) {
        if (_genre.value == slug) return
        _genre.value = slug
        load(reset = true)
    }

    fun setLanguage(lang: String?) {
        if (_language.value == lang) return
        _language.value = lang
        load(reset = true)
    }

    fun setRankWindow(window: NuRankWindow) {
        if (_rankWindow.value == window) return
        _rankWindow.value = window
        if (_mode.value == NuBrowseMode.LEADERBOARD) load(reset = true)
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        load(reset = false)
    }

    private fun sortFor(mode: NuBrowseMode): NuBrowseSort = when (mode) {
        NuBrowseMode.LATEST -> NuBrowseSort.LATEST
        NuBrowseMode.POPULAR -> NuBrowseSort.POPULAR
        NuBrowseMode.SEARCH -> NuBrowseSort.POPULAR
        NuBrowseMode.LEADERBOARD -> NuBrowseSort.POPULAR
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

            runCatching {
                val mode = _mode.value
                if (mode == NuBrowseMode.LEADERBOARD) {
                    repository.ranking(_rankWindow.value, page)
                } else {
                    repository.browse(
                        NuBrowseFilter(
                            query = _query.value.takeIf { mode == NuBrowseMode.SEARCH },
                            sort = sortFor(mode),
                            genreId = _genre.value,
                            language = _language.value,
                        ),
                        page,
                    )
                }
            }.onSuccess { listing ->
                // De-duplicate by URL across pages so the keyed list never
                // collides; stop paginating when a page adds nothing new.
                val merged = (if (reset) listing.results else _results.value + listing.results)
                    .distinctBy { it.url }
                val grew = merged.size > _results.value.size
                _results.value = merged
                _hasMore.value = listing.hasNext && (reset || grew)
            }.onFailure { e ->
                Log.e(TAG, "NU browse failed [mode=${_mode.value} page=$page]", e)
                _error.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
                if (!reset) page--
            }

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}
