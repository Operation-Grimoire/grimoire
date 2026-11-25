package io.grimoire.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.grimoire.ui.theme.premiumGold
import java.text.DateFormat
import java.util.Date

/**
 * Chapter list row: title, upload date and read progress, and a platform [trailing] control
 * such as the lock or download state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterRow(
    name: String,
    uploadDate: Long,
    read: Boolean,
    readProgress: Float,
    locked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    // Read chapters dim to signal "done"; locked chapters instead use a gold
    // accent to signal "premium", so the two states stay visually distinct.
    val contentAlpha = if (read && !locked) 0.38f else 1f
    val headlineColor = if (locked) {
        MaterialTheme.colorScheme.premiumGold
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
    }
    val dateText = remember(uploadDate) {
        if (uploadDate > 0L) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(uploadDate)) else null
    }
    val progressText = if (!read && readProgress > 0f) "${(readProgress * 100).toInt()}%" else null
    val subText = listOfNotNull(dateText, progressText).joinToString(" · ").takeIf { it.isNotEmpty() }

    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        } else {
            ListItemDefaults.colors(containerColor = containerColor)
        },
        headlineContent = {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = headlineColor,
                style = MaterialTheme.typography.bodyMedium)
        },
        supportingContent = if (subText != null) {
            {
                Text(subText, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.bodySmall)
            }
        } else null,
        trailingContent = trailing,
        modifier = modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    )
}
