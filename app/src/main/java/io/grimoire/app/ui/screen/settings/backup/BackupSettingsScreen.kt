package io.grimoire.app.ui.screen.settings.backup

import android.content.Intent
import io.grimoire.app.ui.component.PlainTooltipIconButton
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.IntervalSelector
import io.grimoire.app.ui.screen.settings.TimeOfDayPickerDialog
import io.grimoire.app.ui.screen.settings.formatTimeOfDay
import io.grimoire.app.ui.screen.settings.intervalSummary
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val folderUri by viewModel.folderUri.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val intervalCount by viewModel.intervalCount.collectAsState()
    val intervalUnit by viewModel.intervalUnit.collectAsState()
    val preferredMinutes by viewModel.preferredTimeOfDayMinutes.collectAsState()
    val onlyOnWifi by viewModel.onlyOnWifi.collectAsState()
    val requiresCharging by viewModel.requiresCharging.collectAsState()
    val lastAutoBackupAt by viewModel.lastAutoBackupAt.collectAsState()
    val lastAutoBackupFile by viewModel.lastAutoBackupFile.collectAsState()
    val lastAutoBackupSuccess by viewModel.lastAutoBackupSuccess.collectAsState()
    val lastAutoBackupMessage by viewModel.lastAutoBackupMessage.collectAsState()
    val uiState by viewModel.ui.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pickerMode by rememberSaveable { mutableStateOf(FolderPickerMode.NONE) }
    var showTimePicker by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setFolderUri(uri.toString())
            if (pickerMode == FolderPickerMode.BACKUP_NOW) {
                viewModel.backupNow(uri)
            }
        }
        pickerMode = FolderPickerMode.NONE
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    LaunchedEffect(uiState.event) {
        val msg = when (val ev = uiState.event) {
            is BackupUiEvent.Info -> ev.message
            is BackupUiEvent.Error -> ev.message
            null -> null
        }
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Backup & restore") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader("Backup") }

            item {
                Text(
                    text = "Backups are saved as a gzipped JSON file to a folder you pick on this device. Choose a folder you can find later (such as Downloads or Documents).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Backup folder") },
                    supportingContent = {
                        Text(
                            text = if (folderUri.isBlank()) "Not selected" else displayFolderName(folderUri),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        OutlinedButton(onClick = {
                            pickerMode = FolderPickerMode.SELECT_ONLY
                            folderPicker.launch(defaultInitialUri())
                        }) { Text("Choose") }
                    },
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Button(
                        onClick = {
                            pickerMode = FolderPickerMode.BACKUP_NOW
                            folderPicker.launch(
                                if (folderUri.isNotBlank()) {
                                    runCatching { folderUri.toUri() }.getOrNull()
                                } else defaultInitialUri()
                            )
                        },
                        enabled = !uiState.running,
                        modifier = Modifier.weight(1f),
                    ) { Text("Backup now") }
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(arrayOf("application/gzip", "application/json", "application/octet-stream", "*/*"))
                        },
                        enabled = !uiState.running,
                        modifier = Modifier.weight(1f),
                    ) { Text("Restore…") }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionHeader("Scheduled backup") }

            item {
                Text(
                    text = "Runs in the background on this device. The app does not need to be open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Back up automatically") },
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
                            if (enabled) "Runs around ${formatTimeOfDay(preferredMinutes)}"
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
                    supportingContent = { Text("Skip auto backup on cellular") },
                    trailingContent = {
                        Switch(
                            checked = onlyOnWifi,
                            onCheckedChange = viewModel::setOnlyOnWifi,
                        )
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
                    headlineContent = { Text("Run scheduled backup now") },
                    supportingContent = { Text("Uses the selected folder and schedule constraints") },
                    modifier = Modifier.clickable(
                        enabled = folderUri.isNotBlank() && enabled,
                    ) { viewModel.triggerScheduledBackupNow() },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionHeader("Last automatic backup") }

            item {
                val statusLine = if (lastAutoBackupAt == 0L) {
                    "Never"
                } else {
                    val date = DateFormat.getDateTimeInstance().format(Date(lastAutoBackupAt))
                    if (lastAutoBackupSuccess) {
                        "$date — ${lastAutoBackupFile.ifBlank { "succeeded" }}"
                    } else {
                        "$date — failed"
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(statusLine, style = MaterialTheme.typography.bodyMedium)
                    if (lastAutoBackupMessage.isNotBlank()) {
                        Text(
                            lastAutoBackupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lastAutoBackupSuccess) {
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

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore backup?") },
            text = {
                Text("This will merge the backup into your current library. Existing entries are kept; new ones are added and reading progress is merged.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestoreUri = null
                    viewModel.restoreFrom(uri)
                }) { Text("Restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            },
        )
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

private enum class FolderPickerMode { NONE, SELECT_ONLY, BACKUP_NOW }

private fun displayFolderName(uri: String): String = runCatching {
    val parsed = uri.toUri()
    val last = parsed.lastPathSegment.orEmpty()
    last.substringAfterLast(':').ifBlank { last }.ifBlank { uri }
}.getOrDefault(uri)

private fun defaultInitialUri(): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    // Point the SAF picker at the Downloads folder by default.
    return "content://com.android.externalstorage.documents/document/primary%3ADownload".toUri()
}
