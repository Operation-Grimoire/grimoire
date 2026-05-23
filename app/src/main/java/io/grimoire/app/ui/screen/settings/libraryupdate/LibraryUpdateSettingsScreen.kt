package io.grimoire.app.ui.screen.settings.libraryupdate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.preferences.LibraryUpdateFrequency
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
    val frequency by viewModel.frequency.collectAsState()
    val onlyOnWifi by viewModel.onlyOnWifi.collectAsState()
    val requiresCharging by viewModel.requiresCharging.collectAsState()
    val autoDownloadNewChapters by viewModel.autoDownloadNewChapters.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    val lastRunAt by viewModel.lastRunAt.collectAsState()
    val lastRunSuccess by viewModel.lastRunSuccess.collectAsState()
    val lastRunMessage by viewModel.lastRunMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            items(LibraryUpdateFrequency.entries) { entry ->
                ListItem(
                    leadingContent = {
                        RadioButton(
                            selected = frequency == entry,
                            onClick = { viewModel.setFrequency(entry) },
                        )
                    },
                    headlineContent = { Text(entry.displayName) },
                    modifier = Modifier.clickable { viewModel.setFrequency(entry) },
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

            item {
                ListItem(
                    headlineContent = { Text("Auto-download new chapters") },
                    supportingContent = { Text("Queue new chapters for download as they're found") },
                    trailingContent = {
                        Switch(
                            checked = autoDownloadNewChapters,
                            onCheckedChange = viewModel::setAutoDownloadNewChapters,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setAutoDownloadNewChapters(!autoDownloadNewChapters)
                    },
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

private val LibraryUpdateFrequency.displayName: String
    get() = when (this) {
        LibraryUpdateFrequency.OFF -> "Off"
        LibraryUpdateFrequency.DAILY -> "Daily"
        LibraryUpdateFrequency.EVERY_3_DAYS -> "Every 3 days"
        LibraryUpdateFrequency.WEEKLY -> "Weekly"
    }
