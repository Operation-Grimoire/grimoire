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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.IntervalSelector
import io.grimoire.app.ui.screen.settings.TimeOfDayPickerDialog
import io.grimoire.app.ui.screen.settings.formatTimeOfDay
import io.grimoire.app.ui.screen.settings.intervalSummary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Library updates") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader("Scheduled update") }

            item {
                Text(
                    text = "Refreshes your library in the background, fetching new chapters. The app does not need to be open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Update automatically") },
                    supportingContent = {
                        Text(
                            if (enabled) "Runs every ${intervalSummary(intervalCount, intervalUnit)}"
                            else "Off",
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
                    headlineContent = { Text("Time of day") },
                    supportingContent = {
                        Text(
                            if (enabled) "Run around ${formatTimeOfDay(preferredMinutes)}"
                            else "Runs around ${formatTimeOfDay(preferredMinutes)} when scheduled",
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
                    headlineContent = { Text("Only on Wi-Fi") },
                    supportingContent = { Text("Skip the scheduled update on cellular") },
                    trailingContent = {
                        Switch(checked = onlyOnWifi, onCheckedChange = viewModel::setOnlyOnWifi)
                    },
                    modifier = Modifier.clickable { viewModel.setOnlyOnWifi(!onlyOnWifi) },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Only while charging") },
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
            item { SectionHeader("Sync scope") }

            item {
                ListItem(
                    headlineContent = { Text("Include EPUBs") },
                    supportingContent = {
                        Text("Refresh EPUB novels (local imports and EPUB sources like Z-Library) during library updates. They have no new chapters to fetch, so this is off by default.")
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
            item { SectionHeader("Performance") }

            item {
                ListItem(
                    headlineContent = { Text("Sync concurrency: $concurrency") },
                    supportingContent = {
                        Column {
                            Slider(
                                value = concurrency.toFloat(),
                                onValueChange = { viewModel.setConcurrency(it.toInt()) },
                                valueRange = 1f..8f,
                                steps = 6,
                            )
                            Text(
                                "How many novels to refresh in parallel. Higher is faster but may trigger rate-limiting from some sources.",
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
                    headlineContent = { Text("Update library now") },
                    supportingContent = { Text("Refresh every novel in your library in the background") },
                    modifier = Modifier.clickable {
                        viewModel.updateLibraryNow()
                        scope.launch { snackbarHostState.showSnackbar("Library update queued") }
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionHeader("Last update") }

            item {
                val statusLine = if (lastRunAt == 0L) {
                    "Never"
                } else {
                    val date = DateFormat.getDateTimeInstance().format(Date(lastRunAt))
                    if (lastRunSuccess) "$date — completed" else "$date — failed"
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(statusLine, style = MaterialTheme.typography.bodyMedium)
                    if (lastRunMessage.isNotBlank()) {
                        Text(
                            lastRunMessage,
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
