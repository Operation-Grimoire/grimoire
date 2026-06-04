package io.grimoire.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                SettingsNavItem(
                    icon = Icons.Default.ColorLens,
                    title = "Appearance",
                    subtitle = "Theme, dynamic color",
                    onClick = onNavigateToAppearance,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.TouchApp,
                    title = "Behavior",
                    subtitle = "Haptic feedback",
                    onClick = onNavigateToBehavior,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.LocalLibrary,
                    title = "Library",
                    subtitle = "Display mode, grid columns",
                    onClick = onNavigateToLibrary,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Explore,
                    title = "Browse",
                    subtitle = "Display mode, grid columns",
                    onClick = onNavigateToBrowse,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Language,
                    title = "Languages",
                    subtitle = "App language, source language",
                    onClick = onNavigateToLanguages,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.MenuBook,
                    title = "Reader",
                    subtitle = "Font size, reading direction",
                    onClick = onNavigateToReader,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Text-to-speech",
                    subtitle = "Engine, voices, speech rate",
                    onClick = onNavigateToTts,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Sync,
                    title = "Library updates",
                    subtitle = "Scheduled refresh of your library",
                    onClick = onNavigateToLibraryUpdates,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Backup,
                    title = "Backup & restore",
                    subtitle = "Export, import, scheduled backups",
                    onClick = onNavigateToBackup,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Storage,
                    title = "Data management",
                    subtitle = "Storage usage, clear caches and browse data",
                    onClick = onNavigateToData,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.AutoStories,
                    title = "NovelUpdates",
                    subtitle = "Show the NovelUpdates info panel",
                    onClick = onNavigateToNovelUpdates,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Hub,
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
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
