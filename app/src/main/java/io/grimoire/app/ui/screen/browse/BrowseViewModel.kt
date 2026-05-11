package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Novel
import io.grimoire.api.source.CatalogueSource
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GlobalSearchResult(
    val sourceName: String,
    val packageName: String,
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    val installed: StateFlow<List<ExtensionItem>> = repository.items
        .map { items -> items.filter { it is ExtensionItem.Installed || it is ExtensionItem.InstalledOnly } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

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
            _searchResults.value = sources.map { (name, pkg, _) ->
                GlobalSearchResult(sourceName = name, packageName = pkg, isLoading = true)
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

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }
}
