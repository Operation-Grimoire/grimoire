package io.grimoire.app.ui.screen.settings.library

import io.grimoire.app.ui.icon.*
import androidx.compose.animation.AnimatedVisibility
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHiddenCategories: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val displayMode by viewModel.libraryDisplayMode.collectAsState()
    val gridColumns by viewModel.libraryGridColumns.collectAsState()
    val showAllTab by viewModel.libraryShowAllTab.collectAsState()
    val includeLockedInTotals by viewModel.libraryIncludeLockedInTotals.collectAsState()
    val showReadBadge by viewModel.libraryShowReadBadge.collectAsState()
    val showDownloadedBadge by viewModel.libraryShowDownloadedBadge.collectAsState()
    val showLockedBadge by viewModel.libraryShowLockedBadge.collectAsState()
    val showRatingBadge by viewModel.libraryShowRatingBadge.collectAsState()
    val showEpubBadge by viewModel.libraryShowEpubBadge.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_library_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {

            item { SettingsSectionHeader(stringResource(R.string.settings_section_display)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_display_mode)) },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            LibraryDisplayMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = displayMode == mode,
                                    onClick = { viewModel.setLibraryDisplayMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, LibraryDisplayMode.entries.size),
                                    label = { Text(mode.localizedDisplayName()) },
                                )
                            }
                        }
                    },
                )
            }

            item {
                AnimatedVisibility(visible = displayMode == LibraryDisplayMode.GRID) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_columns)) },
                        supportingContent = {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                listOf(2, 3, 4, 5).forEachIndexed { index, count ->
                                    SegmentedButton(
                                        selected = gridColumns == count,
                                        onClick = { viewModel.setLibraryGridColumns(count) },
                                        shape = SegmentedButtonDefaults.itemShape(index, 4),
                                        label = { Text("$count") },
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item { SettingsSectionHeader(stringResource(R.string.settings_section_organization)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_show_all)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_show_all_summary)) },
                    trailingContent = {
                        Switch(
                            checked = showAllTab,
                            onCheckedChange = { viewModel.setLibraryShowAllTab(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowAllTab(!showAllTab) },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_hidden)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_hidden_summary)) },
                    trailingContent = {
                        Icon(
                            AppIcons.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { onNavigateToHiddenCategories() },
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.library_settings_section_counts)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_locked_totals)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_locked_totals_summary)) },
                    trailingContent = {
                        Switch(
                            checked = includeLockedInTotals,
                            onCheckedChange = { viewModel.setLibraryIncludeLockedInTotals(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryIncludeLockedInTotals(!includeLockedInTotals) },
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.library_settings_section_badges)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_read_badge)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_read_badge_summary)) },
                    trailingContent = {
                        Switch(
                            checked = showReadBadge,
                            onCheckedChange = { viewModel.setLibraryShowReadBadge(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowReadBadge(!showReadBadge) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_downloaded_badge)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_downloaded_badge_summary)) },
                    trailingContent = {
                        Switch(
                            checked = showDownloadedBadge,
                            onCheckedChange = { viewModel.setLibraryShowDownloadedBadge(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowDownloadedBadge(!showDownloadedBadge) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_locked_badge)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_locked_badge_summary)) },
                    trailingContent = {
                        Switch(
                            checked = showLockedBadge,
                            onCheckedChange = { viewModel.setLibraryShowLockedBadge(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowLockedBadge(!showLockedBadge) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_rating_badge)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_rating_badge_summary)) },
                    trailingContent = {
                        Switch(
                            checked = showRatingBadge,
                            onCheckedChange = { viewModel.setLibraryShowRatingBadge(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowRatingBadge(!showRatingBadge) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_settings_epub_badge)) },
                    supportingContent = { Text(stringResource(R.string.library_settings_epub_badge_summary)) },
                    trailingContent = {
                        Switch(
                            checked = showEpubBadge,
                            onCheckedChange = { viewModel.setLibraryShowEpubBadge(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowEpubBadge(!showEpubBadge) },
                )
            }
        }
    }
}

@Composable
private fun LibraryDisplayMode.localizedDisplayName(): String = stringResource(
    when (this) {
        LibraryDisplayMode.GRID -> R.string.settings_display_grid
        LibraryDisplayMode.LIST -> R.string.settings_display_list
    },
)
