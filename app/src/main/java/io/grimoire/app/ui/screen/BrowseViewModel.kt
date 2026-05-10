package io.grimoire.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: ExtensionRepository,
) : ViewModel() {

    val installed: StateFlow<List<ExtensionItem>> = repository.items
        .map { items ->
            items.filter { it is ExtensionItem.Installed || it is ExtensionItem.InstalledOnly }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.refresh() }
    }
}
