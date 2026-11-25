package io.grimoire.app.ui.update

import android.content.Context
import android.widget.Toast
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Description
import io.grimoire.ui.icon.SystemUpdateAlt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.update.DownloadState
import io.grimoire.app.data.update.ReleaseInfo
import io.grimoire.app.R
import io.grimoire.app.ui.component.ActionLinkCard
import io.grimoire.app.ui.component.ValueTransitionPill

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
            val targetVersion = release.displayVersion.substringBefore('+')
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueTransitionPill(
                    from = BuildConfig.VERSION_NAME.substringBefore('+'),
                    to = targetVersion,
                )
                ActionLinkCard(
                    title = stringResource(R.string.about_show_changelog),
                    subtitle = stringResource(R.string.about_show_changelog_summary, targetVersion),
                    leadingIcon = AppIcons.Description,
                    onClick = { openGitHubReleasePage(context, uriHandler, release.releaseUrl) },
                )
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
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.update_skip_version),
                                style = MaterialTheme.typography.labelMedium,
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
