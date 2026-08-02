package io.grimoire.app.ui.screen.browse

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.feature.SearchSource
import io.grimoire.api.source.sourceIdFor
import io.grimoire.app.R
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GlobalSearchResult(
    val sourceName: String,
    val packageName: String,
    val sourceId: Long,
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Projected source list for the Browse home: the pinned sources, the remaining
 * sources grouped by language (both already name/language-filtered), and the full
 * set of available language codes for the filter chips (independent of the active
 * language filter so the chips stay stable).
 */
data class BrowseSourcesUi(
    val pinned: List<ExtensionItem> = emptyList(),
    val byLanguage: Map<String, List<ExtensionItem>> = emptyMap(),
    val languages: List<String> = emptyList(),
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ExtensionRepository,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
    private val browsePreferences: BrowsePreferences,
) : ViewModel() {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    private val localizedContext = AppLocale.wrap(context)

    val libraryKeys: StateFlow<Set<Pair<Long, String>>> = novelDao.getFavoriteKeys()
        .map { keys -> keys.mapTo(HashSet(keys.size)) { it.sourceId to it.url } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val installed: StateFlow<List<ExtensionItem>> = repository.items
        .map { items -> items.filter { it is ExtensionItem.Installed || it is ExtensionItem.InstalledOnly } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Installed extensions with an update available — badges the manage-extensions button. */
    val extensionUpdateCount: StateFlow<Int> = repository.updateCount

    val pinnedPackages: StateFlow<Set<String>> = browsePreferences.pinnedSources.stateIn(viewModelScope)

    /**
     * Held in the VM (not remembered in the screen) so the Browse scroll
     * position survives navigating to a novel and back. rememberLazyListState's
     * saver would clamp the restored index to the top while the source list is
     * momentarily short on return; a persistent state object avoids that.
     */
    val listState = LazyListState()

    val showNovelUpdates: StateFlow<Boolean> = browsePreferences.showNovelUpdates.stateIn(viewModelScope)

    private val duplicatePinned: StateFlow<Boolean> =
        browsePreferences.duplicatePinnedInLanguages.stateIn(viewModelScope)

    private val _nameFilter = MutableStateFlow("")
    val nameFilter: StateFlow<String> = _nameFilter.asStateFlow()

    private val _languageFilter = MutableStateFlow<String?>(null)
    val languageFilter: StateFlow<String?> = _languageFilter.asStateFlow()

    fun setNameFilter(query: String) { _nameFilter.value = query }
    fun setLanguageFilter(lang: String?) { _languageFilter.value = lang }

    /** Pin or unpin a batch of sources at once (used by the selection action bar). */
    fun setPinned(packages: Set<String>, pinned: Boolean) {
        if (packages.isEmpty()) return
        viewModelScope.launch {
            val current = pinnedPackages.value
            val next = if (pinned) current + packages else current - packages
            browsePreferences.pinnedSources.set(next)
        }
    }

    @OptIn(FlowPreview::class)
    val sourcesUi: StateFlow<BrowseSourcesUi> = combine(
        installed,
        pinnedPackages,
        _nameFilter.debounce(120L),
        _languageFilter,
        duplicatePinned,
    ) { sources, pinned, query, langFilter, duplicate ->
        val q = query.trim()
        val languages = sources.map { it.lang.uppercase() }.distinct().sorted()
        val nameMatched =
            if (q.isBlank()) sources else sources.filter { it.name.contains(q, ignoreCase = true) }
        val langMatched =
            if (langFilter == null) nameMatched else nameMatched.filter { it.lang.uppercase() == langFilter }
        // Pinned honours the active language filter too — derive it from the
        // language-matched set, not just the name-matched one.
        val pinnedList = langMatched
            .filter { it.packageName in pinned }
            .sortedBy { it.name.lowercase() }
        // Pinned sources show in the Pinned section; only repeat them under their
        // language group when the user opts into duplicates.
        val langPool = if (duplicate) langMatched else langMatched.filter { it.packageName !in pinned }
        val byLanguage = langPool
            .sortedBy { it.name.lowercase() }
            .groupBy { it.lang.uppercase() }
            .toSortedMap()
        BrowseSourcesUi(pinned = pinnedList, byLanguage = byLanguage, languages = languages)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseSourcesUi())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchResult>> = _searchResults.asStateFlow()

    /**
     * Results ordered for display: sources with hits first (most relevant on
     * top), loading next, empty and failed last. Recomputed off the main
     * thread as per-source responses fold in.
     */
    val sortedSearchResults: StateFlow<List<GlobalSearchResult>> =
        combine(_searchResults, _searchQuery) { results, query ->
            sortGlobalSearchResults(results, query)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null
    private var extendJob: Job? = null

    /**
     * True once the current query has been sent to every source, not just the
     * pinned ones. A search submitted from the Pinned tab stays pinned-only
     * until the user visits a wider tab (the screen then calls
     * [extendSearchToAllSources]) — no background traffic for tabs never seen.
     */
    private val _searchedAllSources = MutableStateFlow(false)
    val searchedAllSources: StateFlow<Boolean> = _searchedAllSources.asStateFlow()

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun setQuery(q: String) {
        _searchQuery.value = q
        if (q.isBlank()) {
            cancelSearches()
            _searchResults.value = emptyList()
            _isSearching.value = false
            _searchedAllSources.value = false
        }
    }

    /**
     * Runs the query against the current tab's scope: only pinned sources when
     * submitted from the Pinned tab, every source otherwise.
     */
    fun submitSearch(pinnedOnly: Boolean) {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        cancelSearches()
        val scopePinned = pinnedOnly && pinnedPackages.value.isNotEmpty()
        _searchedAllSources.value = !scopePinned
        searchJob = viewModelScope.launch {
            val sources = searchableSources().let { all ->
                if (scopePinned) all.filter { it.second in pinnedPackages.value } else all
            }
            if (sources.isEmpty()) {
                _searchResults.value = emptyList()
                _isSearching.value = false
                return@launch
            }

            _isSearching.value = true
            _searchResults.value = sources.map { (name, pkg, src) ->
                GlobalSearchResult(
                    sourceName = name,
                    packageName = pkg,
                    sourceId = sourceIdFor(pkg),
                    isLoading = true,
                )
            }
            runQueries(q, sources)
            _isSearching.value = false
        }
    }

    /**
     * Extends a pinned-only search to the remaining sources — fired when the
     * user first visits the With results / All tab. Loaded results are kept;
     * only the not-yet-queried sources go out.
     */
    fun extendSearchToAllSources() {
        if (_searchedAllSources.value) return
        val q = _searchQuery.value.trim()
        if (q.isBlank() || _searchResults.value.isEmpty()) return
        _searchedAllSources.value = true

        val queriedPkgs = _searchResults.value.mapTo(HashSet()) { it.packageName }
        val toQuery = searchableSources().filter { it.second !in queriedPkgs }
        if (toQuery.isEmpty()) return

        extendJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = _searchResults.value + toQuery.map { (name, pkg, src) ->
                GlobalSearchResult(sourceName = name, packageName = pkg, sourceId = sourceIdFor(pkg), isLoading = true)
            }
            runQueries(q, toQuery)
            _isSearching.value = false
        }
    }

    private fun cancelSearches() {
        searchJob?.cancel()
        extendJob?.cancel()
    }

    /**
     * Every installed catalogue source. Global search always queries them all;
     * the screen's tabs (Pinned / With results / All) narrow the view instead
     * of the query. Each entry is (display name, package, source).
     */
    private fun searchableSources(): List<Triple<String, String, SearchSource>> =
        extensionManager.extensions.value.mapNotNull { loaded ->
            val src = loaded.source as? SearchSource ?: return@mapNotNull null
            val name = loaded.info.label.substringAfter(": ", loaded.info.label)
            Triple(name, loaded.info.packageName, src)
        }

    /** Search each source in parallel, folding each result into its entry by package. */
    private suspend fun runQueries(
        query: String,
        sources: List<Triple<String, String, SearchSource>>,
    ) = coroutineScope {
        sources.map { (_, pkg, src) ->
            async {
                val result = runCatching { src.searchNovels(query, 1, emptyList()) }
                _searchResults.update { current ->
                    current.map { entry ->
                        if (entry.packageName != pkg) return@map entry
                        result.fold(
                            // A source with rotted selectors can answer with
                            // blank entries — invisible cards that still count
                            // as "results". Keep only renderable novels, and
                            // dedupe by url (the lazy-row key).
                            onSuccess = { novels ->
                                val cleaned = novels
                                    .filter { it.url.isNotBlank() && it.title.isNotBlank() }
                                    .distinctBy { it.url }
                                entry.copy(novels = cleaned, isLoading = false)
                            },
                            onFailure = { e -> entry.copy(
                                    isLoading = false,
                                    error = e.message
                                        ?: localizedContext.getString(R.string.error_failed),
                                ) },
                        )
                    }
                }
            }
        }.awaitAll()
    }

    fun clearSearch() {
        cancelSearches()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
        _searchedAllSources.value = false
    }
}
