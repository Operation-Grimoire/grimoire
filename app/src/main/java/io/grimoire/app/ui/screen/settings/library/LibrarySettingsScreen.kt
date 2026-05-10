package io.grimoire.app.ui.screen.settings.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val displayMode by viewModel.libraryDisplayMode.collectAsState()
    val gridColumns by viewModel.libraryGridColumns.collectAsState()
    val showAllTab by viewModel.libraryShowAllTab.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Library") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {

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
