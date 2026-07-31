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

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /**
     * When false (default), global search only queries pinned sources — unless
     * nothing is pinned, in which case every source is searched. Toggling this
     * to true forces all sources regardless of pins.
     */
    private val _includeAllSources = MutableStateFlow(false)
    val includeAllSources: StateFlow<Boolean> = _includeAllSources.asStateFlow()

    fun setIncludeAllSources(value: Boolean) {
        if (_includeAllSources.value == value) return
        _includeAllSources.value = value
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return

        val desired = sourcesForScope()
        val desiredPkgs = desired.mapTo(HashSet()) { it.second }
        // Keep results already loaded for sources still in scope; only query the
        // newly-added ones. Narrowing (all → pinned) just trims, no re-query.
        val kept = _searchResults.value.filter { it.packageName in desiredPkgs }
        val keptPkgs = kept.mapTo(HashSet()) { it.packageName }
        val toQuery = desired.filter { it.second !in keptPkgs }

        if (toQuery.isEmpty()) {
            _searchResults.value = kept
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = kept + toQuery.map { (name, pkg, src) ->
                GlobalSearchResult(sourceName = name, packageName = pkg, sourceId = sourceIdFor(pkg), isLoading = true)
            }
            runQueries(q, toQuery)
            _isSearching.value = false
        }
    }

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun setQuery(q: String) {
        _searchQuery.value = q
        if (q.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            _isSearching.value = false
        }
    }

    fun submitSearch() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val sources = sourcesForScope()
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
     * Installed catalogue sources currently in search scope: pinned-only by
     * default, or all sources when nothing is pinned or [includeAllSources] is on.
     * Each entry is (display name, package, source).
     */
    private fun sourcesForScope(): List<Triple<String, String, SearchSource>> {
        val pinned = pinnedPackages.value
        val pinnedOnly = pinned.isNotEmpty() && !_includeAllSources.value
        return extensionManager.extensions.value.mapNotNull { loaded ->
            val pkg = loaded.info.packageName
            if (pinnedOnly && pkg !in pinned) return@mapNotNull null
            val src = loaded.source as? SearchSource ?: return@mapNotNull null
            val name = loaded.info.label.substringAfter(": ", loaded.info.label)
            Triple(name, pkg, src)
        }
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
                            onSuccess = { novels -> entry.copy(novels = novels, isLoading = false) },
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
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
        // Reset scope so reopening global search defaults back to pinned-only.
        _includeAllSources.value = false
    }
}
