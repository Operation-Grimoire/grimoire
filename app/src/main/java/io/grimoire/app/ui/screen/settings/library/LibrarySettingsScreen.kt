package io.grimoire.app.ui.screen.settings.library

import androidx.compose.animation.AnimatedVisibility
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Library") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {

            item { SettingsSectionHeader("Display") }
            item {
                ListItem(
                    headlineContent = { Text("Display") },
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
                                    label = { Text(mode.displayName) },
                                )
                            }
                        }
                    },
                )
            }

            item {
                AnimatedVisibility(visible = displayMode == LibraryDisplayMode.GRID) {
                    ListItem(
                        headlineContent = { Text("Columns") },
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

            item { SettingsSectionHeader("Organization") }
            item {
                ListItem(
                    headlineContent = { Text("Show \"All\" tab") },
                    supportingContent = { Text("Show a tab combining all categories") },
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
                    headlineContent = { Text("Hidden categories") },
                    supportingContent = { Text("Set a PIN and manage which categories are hidden") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { onNavigateToHiddenCategories() },
                )
            }

            item { SettingsSectionHeader("Chapter counts") }
            item {
                ListItem(
                    headlineContent = { Text("Include locked chapters in totals") },
                    supportingContent = { Text("Count locked chapters in the total and read percentage shown on library badges and the novel details page") },
                    trailingContent = {
                        Switch(
                            checked = includeLockedInTotals,
                            onCheckedChange = { viewModel.setLibraryIncludeLockedInTotals(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryIncludeLockedInTotals(!includeLockedInTotals) },
                )
            }

            item { SettingsSectionHeader("Cover badges") }
            item {
                ListItem(
                    headlineContent = { Text("Show read progress badge") },
                    supportingContent = { Text("Show read count and percentage on library covers") },
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
                    headlineContent = { Text("Show downloaded badge") },
                    supportingContent = { Text("Show the downloaded-chapter count on library covers") },
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
                    headlineContent = { Text("Show locked chapters badge") },
                    supportingContent = { Text("Show a gold lock badge with the locked-chapter count on library covers") },
                    trailingContent = {
                        Switch(
                            checked = showLockedBadge,
                            onCheckedChange = { viewModel.setLibraryShowLockedBadge(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLibraryShowLockedBadge(!showLockedBadge) },
                )
            }
        }
    }
}

private val LibraryDisplayMode.displayName: String
    get() = when (this) {
        LibraryDisplayMode.GRID -> "Grid"
        LibraryDisplayMode.LIST -> "List"
    }
