package io.grimoire.app.ui.screen.settings.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.lang.Language
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

    val available: List<Language> = ContentLanguages.SELECTABLE

    private val _enabled = MutableStateFlow<Set<Language>>(emptySet())
    val enabled: StateFlow<Set<Language>> = _enabled.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            _enabled.value = appLanguages.enabled.changes().first()
        }
    }

    fun toggle(language: Language) {
        _enabled.update { if (language in it) it - language else it + language }
        _saved.value = false
    }

    fun save() {
        val snapshot = _enabled.value
        viewModelScope.launch {
            appLanguages.enabled.set(snapshot)
            extensionManager.reapplyAllPreferences()
            _saved.value = true
        }
    }
}
