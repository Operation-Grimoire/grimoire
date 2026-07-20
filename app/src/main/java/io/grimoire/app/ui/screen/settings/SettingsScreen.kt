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
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R

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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    PlainTooltipIconButton(
                        onClick = onNavigateBack,
                        tooltip = stringResource(R.string.action_back),
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                SettingsNavItem(
                    icon = AppIcons.ColorLens,
                    title = stringResource(R.string.settings_appearance_title),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    onClick = onNavigateToAppearance,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.TouchApp,
                    title = stringResource(R.string.settings_behavior_title),
                    subtitle = stringResource(R.string.settings_behavior_subtitle),
                    onClick = onNavigateToBehavior,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.LocalLibrary,
                    title = stringResource(R.string.settings_library_title),
                    subtitle = stringResource(R.string.settings_library_subtitle),
                    onClick = onNavigateToLibrary,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Explore,
                    title = stringResource(R.string.settings_browse_title),
                    subtitle = stringResource(R.string.settings_browse_subtitle),
                    onClick = onNavigateToBrowse,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Language,
                    title = stringResource(R.string.settings_languages_title),
                    subtitle = stringResource(R.string.settings_languages_subtitle),
                    onClick = onNavigateToLanguages,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.MenuBook,
                    title = stringResource(R.string.settings_reader_title),
                    subtitle = stringResource(R.string.settings_reader_subtitle),
                    onClick = onNavigateToReader,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.RecordVoiceOver,
                    title = stringResource(R.string.settings_tts_title),
                    subtitle = stringResource(R.string.settings_tts_subtitle),
                    onClick = onNavigateToTts,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Sync,
                    title = stringResource(R.string.settings_library_updates_title),
                    subtitle = stringResource(R.string.settings_library_updates_subtitle),
                    onClick = onNavigateToLibraryUpdates,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Backup,
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    onClick = onNavigateToBackup,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Storage,
                    title = stringResource(R.string.settings_data_title),
                    subtitle = stringResource(R.string.settings_data_subtitle),
                    onClick = onNavigateToData,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.AutoStories,
                    title = stringResource(R.string.settings_novelupdates_title),
                    subtitle = stringResource(R.string.settings_novelupdates_subtitle),
                    onClick = onNavigateToNovelUpdates,
                )
            }
            item {
                SettingsNavItem(
                    icon = AppIcons.Hub,
                    title = stringResource(R.string.settings_connections_title),
                    subtitle = stringResource(R.string.settings_connections_subtitle),
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
