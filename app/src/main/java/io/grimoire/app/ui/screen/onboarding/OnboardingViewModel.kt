package io.grimoire.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.preferences.AppLanguagePreferences
import io.grimoire.app.data.preferences.OnboardingPreferences
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appLanguages: AppLanguagePreferences,
    private val onboardingPreferences: OnboardingPreferences,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    /** null while the pref loads (render nothing to avoid a flash), then false/true. */
    val done: StateFlow<Boolean?> = onboardingPreferences.done.changes()
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selected = MutableStateFlow<Set<Language>>(emptySet())
    val selected: StateFlow<Set<Language>> = _selected.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed from anything already stored (update installs), else the system
            // locale + English — a sane default the user can just accept.
            val stored = appLanguages.enabled.changes().first()
            _selected.value = stored.ifEmpty {
                setOfNotNull(Language.EN, ContentLanguages.parse(Locale.getDefault().language))
            }
        }
    }

    fun toggle(language: Language) {
        _selected.update { if (language in it) it - language else it + language }
    }

    fun finish() = viewModelScope.launch {
        appLanguages.enabled.set(_selected.value)
        extensionManager.reapplyAllPreferences()
        onboardingPreferences.done.set(true)
    }
}
