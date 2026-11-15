package io.grimoire.app.ui.update

import android.content.Context
import android.widget.Toast
import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.update.DownloadState
import io.grimoire.app.data.update.ReleaseInfo
import io.grimoire.app.R

@Composable
fun AppUpdateUi(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val availableRelease by viewModel.availableRelease.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    availableRelease?.let { release ->
        UpdateDialog(
            release = release,
            downloadState = downloadState,
            onUpdate = viewModel::startDownload,
            onInstall = viewModel::installUpdate,
            onDismiss = viewModel::dismissUpdate,
            onSkip = viewModel::skipVersion,
        )
    }
}

internal fun openGitHubReleasePage(context: Context, uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
        .onFailure {
            Toast.makeText(context, R.string.crash_no_browser, Toast.LENGTH_SHORT).show()
        }
}

@Composable
private fun UpdateDialog(
    release: ReleaseInfo,
    downloadState: DownloadState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.SystemUpdateAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(
                    if (downloadState is DownloadState.Completed) R.string.update_ready
                    else R.string.update_available,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VersionTransitionRow(
                    current = BuildConfig.VERSION_NAME,
                    target = release.displayVersion,
                )
                TextButton(
                    onClick = { openGitHubReleasePage(context, uriHandler, release.releaseUrl) },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Icon(
                        AppIcons.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_show_changelog))
                }
                when (downloadState) {
                    is DownloadState.Downloading -> DownloadProgressRow(downloadState)
                    is DownloadState.Error -> Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is DownloadState.Completed -> Text(
                        text = stringResource(R.string.update_download_complete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DownloadState.Idle -> {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = stringResource(R.string.update_skip_version),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Downloading -> {
                    Button(onClick = {}, enabled = false) {
                        Text("${downloadPercent(downloadState)}%")
                    }
                }
                is DownloadState.Error -> {
                    Button(onClick = onUpdate) { Text(stringResource(R.string.action_retry)) }
                }
                is DownloadState.Completed -> {
                    Button(onClick = onInstall) { Text(stringResource(R.string.action_install)) }
                }
                DownloadState.Idle -> {
                    Button(onClick = onUpdate) { Text(stringResource(R.string.action_update)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        },
    )
}

@Composable
private fun VersionTransitionRow(current: String, target: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = current,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = AppIcons.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = target,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DownloadProgressRow(state: DownloadState.Downloading) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.totalBytes > 0) {
            LinearProgressIndicator(
                progress = {
                    (state.bytesRead.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = downloadLabel(state),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.update_downloading_background),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun downloadPercent(state: DownloadState.Downloading): Int =
    if (state.totalBytes > 0) (state.bytesRead * 100 / state.totalBytes).toInt().coerceIn(0, 100) else 0

@Composable
private fun downloadLabel(state: DownloadState.Downloading): String {
    val mb: (Long) -> String = { "%.1f".format(it / 1024.0 / 1024.0) }
    return if (state.totalBytes > 0) {
        stringResource(
            R.string.update_download_progress,
            mb(state.bytesRead),
            mb(state.totalBytes),
            downloadPercent(state),
        )
    } else {
        stringResource(R.string.update_download_size, mb(state.bytesRead))
    }
}
