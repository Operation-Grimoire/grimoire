package io.grimoire.app.ui.screen.settings.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.ui.screen.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContentLanguages: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val displayMode by viewModel.browseDisplayMode.collectAsState()
    val gridColumns by viewModel.browseGridColumns.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Browse") },
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
                            BrowseDisplayMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = displayMode == mode,
                                    onClick = { viewModel.setBrowseDisplayMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, BrowseDisplayMode.entries.size),
                                    label = { Text(mode.displayName) },
                                )
                            }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Content languages") },
                    supportingContent = {
                        Text(
                            "Pick which languages multi-language sources show by default",
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToContentLanguages),
                )
            }

            item {
                AnimatedVisibility(visible = displayMode == BrowseDisplayMode.GRID) {
                    ListItem(
                        headlineContent = { Text("Columns") },
                        supportingContent = {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                listOf(2, 3, 4).forEachIndexed { index, count ->
                                    SegmentedButton(
                                        selected = gridColumns == count,
                                        onClick = { viewModel.setBrowseGridColumns(count) },
                                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                                        label = { Text("$count") },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private val BrowseDisplayMode.displayName: String
    get() = when (this) {
        BrowseDisplayMode.GRID -> "Grid"
        BrowseDisplayMode.LIST -> "List"
    }
