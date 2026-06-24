package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.ui.icon.*

/** Bottom sheet of bulk download actions, each enabled per the current download state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsSheet(
    chapters: List<ChapterEntity>,
    onDownloadAll: () -> Unit,
    onDownloadUnread: () -> Unit,
    onDownloadNext: () -> Unit,
    onCancelQueued: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit,
    nextCount: Int = 10,
) {
    val sheetState = rememberModalBottomSheetState()

    val downloaded = chapters.count { it.downloadStatus in ChapterDownloadStatus.HAS_CONTENT_ORDINALS }
    fun undownloaded(c: ChapterEntity) =
        c.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
            c.downloadStatus == ChapterDownloadStatus.ERROR.ordinal
    val canDownloadAll = chapters.any { !it.locked && undownloaded(it) }
    val canDownloadUnread = chapters.any { !it.locked && !it.read && undownloaded(it) }
    val canCancel = chapters.any { it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "$downloaded / ${chapters.size} downloaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            DownloadAction(AppIcons.Download, "Download all", canDownloadAll) { onDownloadAll(); onDismiss() }
            DownloadAction(AppIcons.Download, "Download unread", canDownloadUnread) { onDownloadUnread(); onDismiss() }
            DownloadAction(AppIcons.Download, "Download next $nextCount", canDownloadUnread) { onDownloadNext(); onDismiss() }
            DownloadAction(AppIcons.Close, "Cancel queued downloads", canCancel) { onCancelQueued(); onDismiss() }
            DownloadAction(
                AppIcons.Delete,
                "Delete all downloads",
                enabled = downloaded > 0,
                tint = MaterialTheme.colorScheme.error,
            ) { onDeleteAll(); onDismiss() }
        }
    }
}

@Composable
private fun DownloadAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            headlineColor = color,
            leadingIconColor = color,
        ),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
    )
}
