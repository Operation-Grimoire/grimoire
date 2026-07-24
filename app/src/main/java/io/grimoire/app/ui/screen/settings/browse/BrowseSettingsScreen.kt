package io.grimoire.app.ui.screen.settings.browse

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
import io.grimoire.app.data.preferences.BrowseDisplayMode
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val displayMode by viewModel.browseDisplayMode.collectAsState()
    val gridColumns by viewModel.browseGridColumns.collectAsState()
    val showNovelUpdates by viewModel.browseShowNovelUpdates.collectAsState()
    val duplicatePinned by viewModel.browseDuplicatePinned.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_browse_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_display_mode)) },
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
                                    label = { Text(mode.localizedDisplayName()) },
                                )
                            }
                        }
                    },
                )
            }

            item {
                AnimatedVisibility(visible = displayMode == BrowseDisplayMode.GRID) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_columns)) },
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

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.browse_settings_nu_shortcuts)) },
                    supportingContent = {
                        Text(stringResource(R.string.browse_settings_nu_shortcuts_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = showNovelUpdates,
                            onCheckedChange = viewModel::setBrowseShowNovelUpdates,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setBrowseShowNovelUpdates(!showNovelUpdates)
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.browse_settings_pinned_groups)) },
                    supportingContent = {
                        Text(stringResource(R.string.browse_settings_pinned_groups_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = duplicatePinned,
                            onCheckedChange = viewModel::setBrowseDuplicatePinned,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setBrowseDuplicatePinned(!duplicatePinned)
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowseDisplayMode.localizedDisplayName(): String = stringResource(
    when (this) {
        BrowseDisplayMode.GRID -> R.string.settings_display_grid
        BrowseDisplayMode.LIST -> R.string.settings_display_list
    },
)
