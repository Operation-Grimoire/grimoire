package io.grimoire.app.ui.screen.settings

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToBehavior: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToLanguages: () -> Unit,
    onNavigateToReader: () -> Unit,
    onNavigateToTts: () -> Unit,
    onNavigateToLibraryUpdates: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToNovelUpdates: () -> Unit,
    onNavigateToConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                SettingsNavItem(
                    icon = AppIcons.ColorLens,
                    title = "Appearance",
                    subtitle = "Theme, dynamic color",
                    onClick = onNavigateToAppearance,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.TouchApp,
                    title = "Behavior",
                    subtitle = "Haptic feedback",
                    onClick = onNavigateToBehavior,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.LocalLibrary,
                    title = "Library",
                    subtitle = "Display mode, grid columns",
                    onClick = onNavigateToLibrary,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Explore,
                    title = "Browse",
                    subtitle = "Display mode, grid columns",
                    onClick = onNavigateToBrowse,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Language,
                    title = "Languages",
                    subtitle = "App language, source language",
                    onClick = onNavigateToLanguages,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.MenuBook,
                    title = "Reader",
                    subtitle = "Font size, reading direction",
                    onClick = onNavigateToReader,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.RecordVoiceOver,
                    title = "Text-to-speech",
                    subtitle = "Engine, voices, speech rate",
                    onClick = onNavigateToTts,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Sync,
                    title = "Library updates",
                    subtitle = "Scheduled refresh of your library",
                    onClick = onNavigateToLibraryUpdates,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Backup,
                    title = "Backup & restore",
                    subtitle = "Export, import, scheduled backups",
                    onClick = onNavigateToBackup,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Storage,
                    title = "Data management",
                    subtitle = "Storage usage, clear caches and browse data",
                    onClick = onNavigateToData,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.AutoStories,
                    title = "NovelUpdates",
                    subtitle = "Show the NovelUpdates info panel",
                    onClick = onNavigateToNovelUpdates,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Hub,
                    title = "Connections",
                    subtitle = "External accounts and services",
                    onClick = onNavigateToConnections,
                )
            }
        }
    }
}

@Composable
internal fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                AppIcons.ArrowForwardIos,
                contentDescription = null,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
