package io.grimoire.app.ui.screen.settings.libraryupdate

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.IntervalSelector
import io.grimoire.app.ui.screen.settings.TimeOfDayPickerDialog
import io.grimoire.app.ui.screen.settings.formatTimeOfDay
import io.grimoire.app.ui.screen.settings.intervalSummary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import io.grimoire.app.R
import io.grimoire.app.ui.screen.tasks.localizedTaskSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryUpdateSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryUpdateSettingsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val intervalCount by viewModel.intervalCount.collectAsState()
    val intervalUnit by viewModel.intervalUnit.collectAsState()
    val onlyOnWifi by viewModel.onlyOnWifi.collectAsState()
    val requiresCharging by viewModel.requiresCharging.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    val includeEpubsInSync by viewModel.includeEpubsInSync.collectAsState()
    val preferredMinutes by viewModel.preferredTimeOfDayMinutes.collectAsState()
    val lastRunAt by viewModel.lastRunAt.collectAsState()
    val lastRunSuccess by viewModel.lastRunSuccess.collectAsState()
    val lastRunMessage by viewModel.lastRunMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val queuedMessage = stringResource(R.string.library_update_queued)
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_library_updates_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.library_update_scheduled)) }

            item {
                Text(
                    text = stringResource(R.string.library_update_scheduled_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_update_automatic)) },
                    supportingContent = {
                        Text(
                            if (enabled) stringResource(
                                R.string.schedule_runs_every,
                                intervalSummary(intervalCount, intervalUnit),
                            ) else stringResource(R.string.schedule_off),
                        )
                    },
                    trailingContent = {
                        Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
                    },
                    modifier = Modifier.clickable { viewModel.setEnabled(!enabled) },
                )
            }

            if (enabled) {
                item {
                    IntervalSelector(
                        count = intervalCount,
                        unit = intervalUnit,
                        onCountChange = viewModel::setIntervalCount,
                        onUnitChange = viewModel::setIntervalUnit,
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.schedule_time_of_day)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (enabled) R.string.schedule_run_around
                                else R.string.schedule_runs_around_when_scheduled,
                                formatTimeOfDay(preferredMinutes),
                            ),
                        )
                    },
                    trailingContent = {
                        Text(
                            formatTimeOfDay(preferredMinutes),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    modifier = Modifier.clickable { showTimePicker = true },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_update_wifi_only)) },
                    supportingContent = { Text(stringResource(R.string.library_update_wifi_only_summary)) },
                    trailingContent = {
                        Switch(checked = onlyOnWifi, onCheckedChange = viewModel::setOnlyOnWifi)
                    },
                    modifier = Modifier.clickable { viewModel.setOnlyOnWifi(!onlyOnWifi) },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_update_charging_only)) },
                    trailingContent = {
                        Switch(
                            checked = requiresCharging,
                            onCheckedChange = viewModel::setRequiresCharging,
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setRequiresCharging(!requiresCharging) },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionHeader(stringResource(R.string.library_update_sync_scope)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_update_include_epubs)) },
                    supportingContent = {
                        Text(stringResource(R.string.library_update_include_epubs_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = includeEpubsInSync,
                            onCheckedChange = viewModel::setIncludeEpubsInSync,
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setIncludeEpubsInSync(!includeEpubsInSync) },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionHeader(stringResource(R.string.library_update_performance)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_update_concurrency, concurrency)) },
                    supportingContent = {
                        Column {
                            Slider(
                                value = concurrency.toFloat(),
                                onValueChange = { viewModel.setConcurrency(it.toInt()) },
                                valueRange = 1f..8f,
                                steps = 6,
                            )
                            Text(
                                stringResource(R.string.library_update_concurrency_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_update_now)) },
                    supportingContent = { Text(stringResource(R.string.library_update_now_summary)) },
                    modifier = Modifier.clickable {
                        viewModel.updateLibraryNow()
                        scope.launch { snackbarHostState.showSnackbar(queuedMessage) }
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionHeader(stringResource(R.string.library_update_last)) }

            item {
                val statusLine = if (lastRunAt == 0L) {
                    stringResource(R.string.schedule_never)
                } else {
                    val date = DateFormat.getDateTimeInstance().format(Date(lastRunAt))
                    stringResource(
                        if (lastRunSuccess) R.string.schedule_completed_at else R.string.schedule_failed_at,
                        date,
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(statusLine, style = MaterialTheme.typography.bodyMedium)
                    if (lastRunMessage.isNotBlank()) {
                        Text(
                            localizedTaskSummary(lastRunMessage),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lastRunSuccess) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            item { Column(Modifier.padding(PaddingValues(bottom = 24.dp))) {} }
        }
    }

    if (showTimePicker) {
        TimeOfDayPickerDialog(
            initialHour = preferredMinutes / 60,
            initialMinute = preferredMinutes % 60,
            onConfirm = { hour, minute ->
                viewModel.setPreferredTimeOfDay(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
