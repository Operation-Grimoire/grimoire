package io.grimoire.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.update.ReleaseInfo

@Composable
fun AppUpdateUi(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val changelogText by viewModel.changelogText.collectAsState()
    val availableRelease by viewModel.availableRelease.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()

    changelogText?.let { text ->
        ChangelogDialog(text = text, onDismiss = viewModel::dismissChangelog)
    }

    if (availableRelease != null && changelogText == null) {
        UpdateDialog(
            release = availableRelease!!,
            isDownloading = isDownloading,
            onUpdate = viewModel::downloadAndInstall,
            onDismiss = viewModel::dismissUpdate,
        )
    }
}

@Composable
private fun ChangelogDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun UpdateDialog(
    release: ReleaseInfo,
    isDownloading: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "${release.displayVersion} is available.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (release.releaseNotes.isNotBlank()) {
                    Text(
                        release.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !isDownloading) {
                if (isDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading…")
                    }
                } else {
                    Text("Update")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
