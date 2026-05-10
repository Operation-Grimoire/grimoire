package io.grimoire.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.ThemeMode
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.data.preferences.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val uiPreferences: UiPreferences,
    private val browsePreferences: BrowsePreferences,
) : ViewModel() {

    val themeMode = uiPreferences.themeMode.stateIn(viewModelScope)
    val useDynamicColor = uiPreferences.useDynamicColor.stateIn(viewModelScope)
    val browseDisplayMode = browsePreferences.displayMode.stateIn(viewModelScope)
    val browseGridColumns = browsePreferences.gridColumns.stateIn(viewModelScope)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { uiPreferences.themeMode.set(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { uiPreferences.useDynamicColor.set(enabled) }
    fun setBrowseDisplayMode(mode: BrowseDisplayMode) = viewModelScope.launch { browsePreferences.displayMode.set(mode) }
    fun setBrowseGridColumns(columns: Int) = viewModelScope.launch { browsePreferences.gridColumns.set(columns.coerceIn(2, 4)) }
}
