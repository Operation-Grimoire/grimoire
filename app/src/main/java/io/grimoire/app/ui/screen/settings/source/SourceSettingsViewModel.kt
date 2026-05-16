package io.grimoire.app.ui.screen.settings.source

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.source.ConfigurableSource
import io.grimoire.api.source.SourcePreference
import io.grimoire.app.data.preferences.SourceSettingsPreferences
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val sourceSettings: SourceSettingsPreferences,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])

    private val loaded get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }

    val sourceName: String =
        loaded?.info?.label?.substringAfter(": ", loaded?.info?.label.orEmpty()).orEmpty()
            .ifEmpty { pkg }

    val preferences: List<SourcePreference> =
        (loaded?.source as? ConfigurableSource)?.getPreferences().orEmpty()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            val keys = preferences.map { it.key }
            val stored = sourceSettings.snapshot(pkg, keys)
            _values.value = preferences.associate { pref ->
                val current = stored[pref.key].orEmpty()
                pref.key to current.ifEmpty { defaultValue(pref) }
            }
        }
    }

    private fun defaultValue(pref: SourcePreference): String = when (pref) {
        is SourcePreference.EditText -> pref.default
        is SourcePreference.Switch -> pref.default.toString()
    }

    fun update(key: String, value: String) {
        _values.update { it + (key to value) }
        _saved.value = false
    }

    fun save() {
        val snapshot = _values.value
        viewModelScope.launch {
            snapshot.forEach { (key, value) -> sourceSettings.pref(pkg, key).set(value) }
            extensionManager.reapplyPreferences(pkg)
            _saved.value = true
        }
    }
}
