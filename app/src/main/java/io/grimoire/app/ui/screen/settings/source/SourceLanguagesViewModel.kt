package io.grimoire.app.ui.screen.settings.source

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.lang.Language
import io.grimoire.api.source.feature.MultiLanguageSource
import io.grimoire.app.data.preferences.AppLanguagePreferences
import io.grimoire.app.data.preferences.SourceSettingsPreferences
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the per-source content-language picker. Lets the user opt this source
 * out of the global preference (via the override toggle) and pick a different
 * subset for it. The list of languages shown is the intersection of
 * [ContentLanguages.SELECTABLE] and the source's `availableLanguages()` — only
 * languages this source actually serves.
 */
@HiltViewModel
class SourceLanguagesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val sourceSettings: SourceSettingsPreferences,
    private val appLanguages: AppLanguagePreferences,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])

    private val loaded = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }

    val sourceName: String =
        loaded?.info?.label?.substringAfter(": ", loaded?.info?.label.orEmpty()).orEmpty()
            .ifEmpty { pkg }

    /**
     * Languages this source advertises, restricted to the common app list.
     * If the source advertises none (or isn't multi-language but `lang == "all"`),
     * fall back to the full common list so the user still has something to pick.
     * Loaded asynchronously since `availableLanguages()` may scrape the site.
     */
    private val _available = MutableStateFlow(ContentLanguages.SELECTABLE)
    val available: StateFlow<List<Language>> = _available.asStateFlow()

    private val _override = MutableStateFlow(false)
    val override: StateFlow<Boolean> = _override.asStateFlow()

    private val _enabled = MutableStateFlow<Set<Language>>(emptySet())
    val enabled: StateFlow<Set<Language>> = _enabled.asStateFlow()

    private val _globalSet = MutableStateFlow<Set<Language>>(emptySet())
    val globalSet: StateFlow<Set<Language>> = _globalSet.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            val advertised = runCatching {
                (loaded?.source as? MultiLanguageSource)?.availableLanguages().orEmpty()
            }.getOrDefault(emptyList()).toSet()
            _available.value = if (advertised.isEmpty()) {
                ContentLanguages.SELECTABLE
            } else {
                ContentLanguages.SELECTABLE.filter { it in advertised }
                    .ifEmpty { advertised.toList() }
            }
        }
        viewModelScope.launch {
            _override.value = sourceSettings.contentLanguagesOverride(pkg).changes().first()
            _enabled.value = sourceSettings.contentLanguages(pkg).changes().first()
            _globalSet.value = appLanguages.enabled.changes().first()
            // When override is off, the picker shows the global set (read-only).
            // Seed _enabled with the global set so toggling the override on
            // doesn't drop the user into an empty selection.
            if (!_override.value && _enabled.value.isEmpty()) {
                _enabled.value = _globalSet.value
            }
        }
    }

    fun setOverride(enabled: Boolean) {
        _override.value = enabled
        if (enabled && _enabled.value.isEmpty()) {
            // User just flipped override on — start from the global set so the
            // UI matches "Using global" the moment before, then they can edit.
            _enabled.value = _globalSet.value
        }
        _saved.value = false
    }

    fun toggle(language: Language) {
        if (!_override.value) return
        _enabled.update { if (language in it) it - language else it + language }
        _saved.value = false
    }

    fun save() {
        val overrideOn = _override.value
        val snapshot = _enabled.value
        viewModelScope.launch {
            sourceSettings.contentLanguagesOverride(pkg).set(overrideOn)
            if (overrideOn) {
                sourceSettings.contentLanguages(pkg).set(snapshot)
            }
            extensionManager.reapplyPreferences(pkg)
            _saved.value = true
        }
    }
}
