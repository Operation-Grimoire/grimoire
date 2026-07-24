package io.grimoire.app.ui.component

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.ExperimentalFoundationApi
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.ui.theme.premiumGold
import io.grimoire.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standard chapter row used by Novel Details and Updates. Renders the chapter
 * title, optional upload date / read progress in the supporting line, and a
 * trailing icon that reflects lock + download state. In selection mode, the
 * trailing icon becomes non-interactive so a row tap toggles selection rather
 * than firing the underlying action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterItem(
    chapter: ChapterEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onRedownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read chapters dim to signal "done"; locked chapters instead use a gold
    // accent to signal "premium", so the two states stay visually distinct.
    val contentAlpha = if (chapter.read && !chapter.locked) 0.38f else 1f
    val headlineColor = if (chapter.locked) {
        MaterialTheme.colorScheme.premiumGold
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
    }
    val dateText = remember(chapter.uploadDate) {
        if (chapter.uploadDate > 0L) formatChapterDate(chapter.uploadDate) else null
    }
    val progressText = if (!chapter.read && chapter.readProgress > 0f) {
        "${(chapter.readProgress * 100).toInt()}%"
    } else null
    val subText = listOfNotNull(dateText, progressText).joinToString(" · ").takeIf { it.isNotEmpty() }

    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        } else {
            ListItemDefaults.colors()
        },
        headlineContent = {
            Text(
                chapter.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = headlineColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = if (subText != null) {
            {
                Text(
                    subText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else null,
        trailingContent = {
            ChapterStatusTrailing(
                chapter = chapter,
                selectionMode = selectionMode,
                onLockedClick = onLockedClick,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDeleteDownload = onDeleteDownload,
                onRedownload = onRedownload,
            )
        },
        modifier = modifier.combinedClickable(
            onClick = when {
                selectionMode -> onToggleSelection
                chapter.locked -> onLockedClick
                else -> onClick
            },
            onLongClick = onToggleSelection,
        ),
    )
}

/**
 * Trailing affordance for a chapter row — lock badge, download button, queued
 * cancel, in-flight progress, downloaded checkmark, or failed retry/cancel
 * pair. Non-interactive in selection mode so the row's tap handles selection.
 */
@Composable
fun ChapterStatusTrailing(
    chapter: ChapterEntity,
    selectionMode: Boolean,
    onLockedClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onRedownload: () -> Unit,
) {
    val dlStatus = ChapterDownloadStatus.entries.getOrElse(chapter.downloadStatus) { ChapterDownloadStatus.NONE }
    when {
        chapter.locked -> ChapterTrailingIcon(
            icon = AppIcons.Lock,
            description = stringResource(R.string.content_description_locked),
            tint = MaterialTheme.colorScheme.premiumGold,
            onClick = if (selectionMode) null else onLockedClick,
        )
        dlStatus == ChapterDownloadStatus.NONE -> ChapterTrailingIcon(
            icon = AppIcons.Download,
            description = stringResource(R.string.library_download),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = if (selectionMode) null else onDownload,
        )
        dlStatus == ChapterDownloadStatus.QUEUED -> ChapterTrailingIcon(
            icon = AppIcons.Close,
            description = stringResource(R.string.chapter_cancel_download),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = if (selectionMode) null else onCancelDownload,
        )
        dlStatus == ChapterDownloadStatus.DOWNLOADING ->
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(modifier = Modifier.width(24.dp))
            }
        dlStatus == ChapterDownloadStatus.DOWNLOADED -> DownloadedRowAction(
            description = stringResource(R.string.status_downloaded),
            badge = null,
            selectionMode = selectionMode,
            menuActions = listOf(
                stringResource(R.string.downloads_redownload) to onRedownload,
                stringResource(R.string.downloads_delete_download) to onDeleteDownload,
            ),
        )
        dlStatus == ChapterDownloadStatus.REDOWNLOAD_QUEUED -> DownloadedRowAction(
            description = stringResource(R.string.chapter_downloaded_refresh_queued),
            badge = { RefreshBadgeDot(color = MaterialTheme.colorScheme.onSurfaceVariant) },
            selectionMode = selectionMode,
            menuActions = listOf(
                stringResource(R.string.chapter_cancel_refresh) to onCancelDownload,
                stringResource(R.string.downloads_delete_download) to onDeleteDownload,
            ),
        )
        dlStatus == ChapterDownloadStatus.REDOWNLOADING -> DownloadedRowAction(
            description = stringResource(R.string.chapter_downloaded_refreshing),
            badge = { RefreshBadgeSpinner() },
            selectionMode = selectionMode,
            menuActions = null, // non-interactive, matches plain DOWNLOADING
        )
        dlStatus == ChapterDownloadStatus.REDOWNLOAD_ERROR -> DownloadedRowAction(
            description = stringResource(R.string.chapter_downloaded_refresh_failed),
            badge = { RefreshBadgeDot(color = MaterialTheme.colorScheme.error) },
            selectionMode = selectionMode,
            menuActions = listOf(
                stringResource(R.string.downloads_redownload) to onRedownload,
                stringResource(R.string.downloads_delete_download) to onDeleteDownload,
            ),
        )
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            ChapterTrailingIcon(
                icon = AppIcons.Close,
                description = stringResource(R.string.action_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = if (selectionMode) null else onDeleteDownload,
                buttonSize = 40.dp,
                iconSize = 20.dp,
            )
            ChapterTrailingIcon(
                icon = AppIcons.Refresh,
                description = stringResource(R.string.action_retry),
                tint = MaterialTheme.colorScheme.error,
                onClick = if (selectionMode) null else onDownload,
                buttonSize = 40.dp,
                iconSize = 20.dp,
            )
        }
    }
}

// A null onClick renders a plain, non-interactive icon with the same footprint.
@Composable
fun ChapterTrailingIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: (() -> Unit)?,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    if (onClick != null) {
        PlainTooltipIconButton(onClick = onClick, modifier = Modifier.size(buttonSize), tooltip = description) {
            Icon(icon, contentDescription = description, tint = tint,
                modifier = Modifier.size(iconSize))
        }
    } else {
        Box(Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint,
                modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * Trailing affordance shared by all the "row has saved content" states (DOWNLOADED and the three
 * REDOWNLOAD_* mid-refresh states). Renders the green DownloadDone icon as the base, optionally
 * overlays a small corner [badge], and opens a dropdown menu when tapped if [menuActions] is set.
 */
@Composable
private fun DownloadedRowAction(
    description: String,
    badge: (@Composable BoxScope.() -> Unit)?,
    selectionMode: Boolean,
    menuActions: List<Pair<String, () -> Unit>>?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tapHandler: (() -> Unit)? = when {
        selectionMode -> null
        menuActions == null -> null
        else -> ({ menuExpanded = true })
    }
    Box {
        ChapterTrailingIcon(
            icon = AppIcons.DownloadDone,
            description = description,
            tint = MaterialTheme.colorScheme.primary,
            onClick = tapHandler,
        )
        if (badge != null) badge()
        if (menuActions != null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                for ((label, action) in menuActions) {
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuExpanded = false
                            action()
                        },
                    )
                }
            }
        }
    }
}

/** Small solid dot rendered in the corner of a 48dp icon button. */
@Composable
private fun BoxScope.RefreshBadgeDot(color: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Tiny spinner rendered in the corner of a 48dp icon button. */
@Composable
private fun BoxScope.RefreshBadgeSpinner() {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .size(14.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
    }
}

private fun formatChapterDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
