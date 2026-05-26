package io.grimoire.app.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.update.ChangelogCategory
import io.grimoire.app.data.update.ChangelogItem
import io.grimoire.app.data.update.ChangelogParser
import io.grimoire.app.data.update.ChangelogSection
import io.grimoire.app.data.update.DownloadState
import io.grimoire.app.data.update.ReleaseInfo

@Composable
fun AppUpdateUi(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val changelogText by viewModel.changelogText.collectAsState()
    val availableRelease by viewModel.availableRelease.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    changelogText?.let { text ->
        ChangelogDialog(text = text, onDismiss = viewModel::dismissChangelog)
    }

    if (availableRelease != null && changelogText == null) {
        UpdateDialog(
            release = availableRelease!!,
            downloadState = downloadState,
            onUpdate = viewModel::startDownload,
            onInstall = viewModel::installUpdate,
            onDismiss = viewModel::dismissUpdate,
            onSkip = viewModel::skipVersion,
        )
    }
}

@Composable
private fun ChangelogDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParsedReleaseNotes(text)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun ParsedReleaseNotes(text: String) {
    if (text.isBlank()) return
    val sections = remember(text) { ChangelogParser.parse(text) }
    if (sections.isEmpty()) {
        // Parser found no list items — show the raw body so an unfamiliar
        // shape never produces an empty surface.
        Text(text, style = MaterialTheme.typography.bodyMedium)
    } else {
        sections.forEach { section -> ChangelogSectionCard(section) }
    }
}

@Composable
private fun ChangelogSectionCard(section: ChangelogSection) {
    // Start collapsed so the dialog opens at a glanceable height — section
    // headers + counts read as a summary, taps reveal the individual items.
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "changelog-chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = section.category.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = section.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = section.items.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                section.items.forEach { item -> ChangelogItemRow(item) }
            }
        }
    }
}

@Composable
private fun ChangelogItemRow(item: ChangelogItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            val meta = buildString {
                item.prNumber?.let { append("#").append(it) }
                if (item.prNumber != null && item.author != null) append(" · ")
                item.author?.let { append("@").append(it) }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ChangelogCategory.icon(): ImageVector = when (this) {
    ChangelogCategory.FEATURES -> Icons.Filled.AutoAwesome
    ChangelogCategory.BUG_FIXES -> Icons.Filled.BugReport
    ChangelogCategory.SOURCES -> Icons.Filled.Extension
    ChangelogCategory.DOCUMENTATION -> Icons.Filled.Description
    ChangelogCategory.OTHER -> Icons.Filled.Tune
    ChangelogCategory.CHANGES -> Icons.Filled.List
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
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdateAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(if (downloadState is DownloadState.Completed) "Update ready" else "Update available")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VersionTransitionRow(
                    current = BuildConfig.VERSION_NAME,
                    target = release.displayVersion,
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ParsedReleaseNotes(release.releaseNotes)
                }
                when (downloadState) {
                    is DownloadState.Downloading -> DownloadProgressRow(downloadState)
                    is DownloadState.Error -> Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is DownloadState.Completed -> Text(
                        text = "Download complete — tap Install to finish updating.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DownloadState.Idle -> {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = "Skip this version",
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
                    Button(onClick = onUpdate) { Text("Retry") }
                }
                is DownloadState.Completed -> {
                    Button(onClick = onInstall) { Text("Install") }
                }
                DownloadState.Idle -> {
                    Button(onClick = onUpdate) { Text("Update") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
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
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
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
            text = "Downloading in the background — you can close the app.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun downloadPercent(state: DownloadState.Downloading): Int =
    if (state.totalBytes > 0) (state.bytesRead * 100 / state.totalBytes).toInt().coerceIn(0, 100) else 0

private fun downloadLabel(state: DownloadState.Downloading): String {
    val mb: (Long) -> String = { "%.1f".format(it / 1024.0 / 1024.0) }
    return if (state.totalBytes > 0) {
        "${mb(state.bytesRead)} / ${mb(state.totalBytes)} MB · ${downloadPercent(state)}%"
    } else {
        "${mb(state.bytesRead)} MB"
    }
}
