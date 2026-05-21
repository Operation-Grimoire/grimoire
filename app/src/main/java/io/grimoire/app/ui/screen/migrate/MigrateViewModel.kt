package io.grimoire.app.ui.screen.migrate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.source.CatalogueSource
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.ui.screen.browse.GlobalSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the migration search screen: a global search, pre-seeded with the
 * source novel's title, used to find the same novel on another source. Picking
 * a result opens its detail screen, where the migration is confirmed.
 */
@HiltViewModel
class MigrateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
) : ViewModel() {

    private val sourceNovelId: Long = checkNotNull(savedStateHandle["novelId"])

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Pre-seed the search with the novel's title and run it straight away.
        viewModelScope.launch {
            val novel = novelDao.getById(sourceNovelId) ?: return@launch
            _searchQuery.value = novel.title
            submitSearch()
        }
    }

    fun setQuery(q: String) {
        _searchQuery.value = q
    }

    /** Queries every installed catalogue source in parallel for [searchQuery]. */
    fun submitSearch() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val sources = extensionManager.extensions.value.mapNotNull { loaded ->
                val src = loaded.source as? CatalogueSource ?: return@mapNotNull null
                val name = loaded.info.label.substringAfter(": ", loaded.info.label)
                Triple(name, loaded.info.packageName, src)
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
                    sourceId = src.id,
                    isLoading = true,
                )
            }

            sources.map { (_, pkg, src) ->
                async {
                    val result = runCatching { src.searchNovels(q, 1, emptyList()) }
                    _searchResults.update { current ->
                        current.map { entry ->
                            if (entry.packageName != pkg) return@map entry
                            result.fold(
                                onSuccess = { novels -> entry.copy(novels = novels, isLoading = false) },
                                onFailure = { e -> entry.copy(isLoading = false, error = e.message ?: "Failed") },
                            )
                        }
                    }
                }
            }.awaitAll()

            _isSearching.value = false
        }
    }
}
