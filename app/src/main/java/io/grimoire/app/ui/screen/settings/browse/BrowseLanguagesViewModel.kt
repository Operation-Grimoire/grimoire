package io.grimoire.app.ui.screen.settings.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.AppLanguagePreferences
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseLanguagesViewModel @Inject constructor(
    private val appLanguages: AppLanguagePreferences,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    val available: List<String> = ContentLanguages.ALL

    private val _enabled = MutableStateFlow<Set<String>>(emptySet())
    val enabled: StateFlow<Set<String>> = _enabled.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            _enabled.value = appLanguages.enabled.changes().first()
        }
    }

    fun toggle(name: String) {
        val key = ContentLanguages.normalize(name)
        _enabled.update { if (key in it) it - key else it + key }
        _saved.value = false
    }

    fun save() {
        val snapshot = ContentLanguages.normalize(_enabled.value)
        viewModelScope.launch {
            appLanguages.enabled.set(snapshot)
            extensionManager.reapplyAllPreferences()
            _saved.value = true
        }
    }
}
