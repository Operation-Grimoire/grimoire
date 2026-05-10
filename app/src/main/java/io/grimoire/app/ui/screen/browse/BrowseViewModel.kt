package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Novel
import io.grimoire.api.source.CatalogueSource
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GlobalSearchResult(
    val sourceName: String,
    val packageName: String,
    val novels: List<Novel>,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    val installed: StateFlow<List<ExtensionItem>> = repository.items
        .map { items ->
            items.filter { it is ExtensionItem.Installed || it is ExtensionItem.InstalledOnly }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun setQuery(q: String) {
        _searchQuery.value = q
        if (q.isBlank()) _searchResults.value = emptyList()
    }

    fun submitSearch() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()

            val results = extensionManager.extensions.value
                .mapNotNull { loaded ->
                    val src = loaded.source as? CatalogueSource ?: return@mapNotNull null
                    val name = loaded.info.label.substringAfter(": ", loaded.info.label)
                    Triple(name, loaded.info.packageName, src)
                }
                .map { (name, pkg, src) ->
                    async {
                        runCatching { src.searchNovels(q, 1, emptyList()) }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { GlobalSearchResult(name, pkg, it) }
                    }
                }
                .awaitAll()
                .filterNotNull()

            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }
}
