package io.grimoire.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.data.preferences.ReaderPreferences
import io.grimoire.app.data.preferences.ThemeMode
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.data.preferences.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val uiPreferences: UiPreferences,
    private val browsePreferences: BrowsePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {

    val themeMode = uiPreferences.themeMode.stateIn(viewModelScope)
    val useDynamicColor = uiPreferences.useDynamicColor.stateIn(viewModelScope)
    val browseDisplayMode = browsePreferences.displayMode.stateIn(viewModelScope)
    val browseGridColumns = browsePreferences.gridColumns.stateIn(viewModelScope)
    val libraryDisplayMode = libraryPreferences.displayMode.stateIn(viewModelScope)
    val libraryGridColumns = libraryPreferences.gridColumns.stateIn(viewModelScope)
    val libraryShowAllTab = libraryPreferences.showAllTab.stateIn(viewModelScope)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { uiPreferences.themeMode.set(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { uiPreferences.useDynamicColor.set(enabled) }
    fun setBrowseDisplayMode(mode: BrowseDisplayMode) = viewModelScope.launch { browsePreferences.displayMode.set(mode) }
    fun setBrowseGridColumns(columns: Int) = viewModelScope.launch { browsePreferences.gridColumns.set(columns.coerceIn(2, 4)) }
    fun setLibraryDisplayMode(mode: LibraryDisplayMode) = viewModelScope.launch { libraryPreferences.displayMode.set(mode) }
    fun setLibraryGridColumns(columns: Int) = viewModelScope.launch { libraryPreferences.gridColumns.set(columns.coerceIn(2, 5)) }
    fun setLibraryShowAllTab(show: Boolean) = viewModelScope.launch { libraryPreferences.showAllTab.set(show) }

    val readerMarkAsReadThreshold = readerPreferences.markAsReadThreshold.stateIn(viewModelScope)
    fun setReaderMarkAsReadThreshold(value: Int) = viewModelScope.launch { readerPreferences.markAsReadThreshold.set(value.coerceIn(50, 100)) }

    val readerOrientation = readerPreferences.orientation.stateIn(viewModelScope)
    fun setReaderOrientation(value: ReaderOrientation) = viewModelScope.launch { readerPreferences.orientation.set(value) }

    val readerHideNotificationBar = readerPreferences.hideNotificationBar.stateIn(viewModelScope)
    fun setReaderHideNotificationBar(value: Boolean) = viewModelScope.launch { readerPreferences.hideNotificationBar.set(value) }
}
