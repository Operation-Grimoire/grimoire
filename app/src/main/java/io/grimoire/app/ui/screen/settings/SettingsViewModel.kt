package io.grimoire.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.ColorTheme
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.data.preferences.NovelUpdatesPreferences
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
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
    private val novelUpdatesPreferences: NovelUpdatesPreferences,
) : ViewModel() {

    val novelUpdatesEnabled = novelUpdatesPreferences.enabled.stateIn(viewModelScope)
    fun setNovelUpdatesEnabled(enabled: Boolean) =
        viewModelScope.launch { novelUpdatesPreferences.enabled.set(enabled) }

    val themeMode = uiPreferences.themeMode.stateIn(viewModelScope)
    val useDynamicColor = uiPreferences.useDynamicColor.stateIn(viewModelScope)
    val colorTheme = uiPreferences.colorTheme.stateIn(viewModelScope)
    val hapticsEnabled = uiPreferences.hapticsEnabled.stateIn(viewModelScope)
    val browseDisplayMode = browsePreferences.displayMode.stateIn(viewModelScope)
    val browseGridColumns = browsePreferences.gridColumns.stateIn(viewModelScope)
    val libraryDisplayMode = libraryPreferences.displayMode.stateIn(viewModelScope)
    val libraryGridColumns = libraryPreferences.gridColumns.stateIn(viewModelScope)
    val libraryShowAllTab = libraryPreferences.showAllTab.stateIn(viewModelScope)
    val libraryIncludeLockedInTotals = libraryPreferences.includeLockedInTotals.stateIn(viewModelScope)
    val libraryShowReadBadge = libraryPreferences.showReadBadge.stateIn(viewModelScope)
    val libraryShowDownloadedBadge = libraryPreferences.showDownloadedBadge.stateIn(viewModelScope)
    val libraryShowLockedBadge = libraryPreferences.showLockedBadge.stateIn(viewModelScope)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { uiPreferences.themeMode.set(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { uiPreferences.useDynamicColor.set(enabled) }
    fun setColorTheme(theme: ColorTheme) = viewModelScope.launch { uiPreferences.colorTheme.set(theme) }
    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch { uiPreferences.hapticsEnabled.set(enabled) }
    fun setBrowseDisplayMode(mode: BrowseDisplayMode) = viewModelScope.launch { browsePreferences.displayMode.set(mode) }
    fun setBrowseGridColumns(columns: Int) = viewModelScope.launch { browsePreferences.gridColumns.set(columns.coerceIn(2, 4)) }
    fun setLibraryDisplayMode(mode: LibraryDisplayMode) = viewModelScope.launch { libraryPreferences.displayMode.set(mode) }
    fun setLibraryGridColumns(columns: Int) = viewModelScope.launch { libraryPreferences.gridColumns.set(columns.coerceIn(2, 5)) }
    fun setLibraryShowAllTab(show: Boolean) = viewModelScope.launch { libraryPreferences.showAllTab.set(show) }
    fun setLibraryIncludeLockedInTotals(value: Boolean) = viewModelScope.launch { libraryPreferences.includeLockedInTotals.set(value) }
    fun setLibraryShowReadBadge(value: Boolean) = viewModelScope.launch { libraryPreferences.showReadBadge.set(value) }
    fun setLibraryShowDownloadedBadge(value: Boolean) = viewModelScope.launch { libraryPreferences.showDownloadedBadge.set(value) }
    fun setLibraryShowLockedBadge(value: Boolean) = viewModelScope.launch { libraryPreferences.showLockedBadge.set(value) }

    val readerMarkAsReadStrategy = readerPreferences.markAsReadStrategy.stateIn(viewModelScope)
    fun setReaderMarkAsReadStrategy(value: MarkAsReadStrategy) =
        viewModelScope.launch { readerPreferences.markAsReadStrategy.set(value) }

    val readerMarkAsReadThreshold = readerPreferences.markAsReadThreshold.stateIn(viewModelScope)
    fun setReaderMarkAsReadThreshold(value: Int) =
        viewModelScope.launch { readerPreferences.markAsReadThreshold.set(value.coerceIn(50, 100)) }

    val readerMarkAsReadParagraphsFromEnd = readerPreferences.markAsReadParagraphsFromEnd.stateIn(viewModelScope)
    fun setReaderMarkAsReadParagraphsFromEnd(value: Int) =
        viewModelScope.launch { readerPreferences.markAsReadParagraphsFromEnd.set(value.coerceIn(0, 20)) }

    val readerOrientation = readerPreferences.orientation.stateIn(viewModelScope)
    fun setReaderOrientation(value: ReaderOrientation) = viewModelScope.launch { readerPreferences.orientation.set(value) }

    val readerHideNotificationBar = readerPreferences.hideNotificationBar.stateIn(viewModelScope)
    fun setReaderHideNotificationBar(value: Boolean) = viewModelScope.launch { readerPreferences.hideNotificationBar.set(value) }

    val readerHideInlineImages = readerPreferences.hideInlineImages.stateIn(viewModelScope)
    fun setReaderHideInlineImages(value: Boolean) = viewModelScope.launch { readerPreferences.hideInlineImages.set(value) }

    val readerColorTheme = readerPreferences.colorTheme.stateIn(viewModelScope)
    fun setReaderColorTheme(value: ReaderColorTheme) = viewModelScope.launch { readerPreferences.colorTheme.set(value) }

    val readerFont = readerPreferences.readerFont.stateIn(viewModelScope)
    fun setReaderFont(value: ReaderFont) = viewModelScope.launch { readerPreferences.readerFont.set(value) }

    val readerFontSize = readerPreferences.fontSize.stateIn(viewModelScope)
    fun setReaderFontSize(value: Int) = viewModelScope.launch { readerPreferences.fontSize.set(value.coerceIn(12, 32)) }

    val readerLineHeightTimes10 = readerPreferences.lineHeightTimes10.stateIn(viewModelScope)
    fun setReaderLineHeight(times10: Int) = viewModelScope.launch { readerPreferences.lineHeightTimes10.set(times10.coerceIn(10, 30)) }

    val readerParagraphSpacing = readerPreferences.paragraphSpacing.stateIn(viewModelScope)
    fun setReaderParagraphSpacing(dp: Int) = viewModelScope.launch { readerPreferences.paragraphSpacing.set(dp.coerceIn(0, 32)) }
}
