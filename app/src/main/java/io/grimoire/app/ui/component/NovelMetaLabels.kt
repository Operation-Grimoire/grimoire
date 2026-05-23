package io.grimoire.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.NovelStatus
import java.util.Locale

@Composable
fun StatusLabel(status: NovelStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            status.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RatingLabel(
    rating: Float,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = rating.coerceIn(0f, 5f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = buildString {
                append(String.format(Locale.getDefault(), "%.1f", clamped))
                if (count != null && count > 0) append(" (").append(count).append(')')
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

val NovelStatus.displayName: String
    get() = when (this) {
        NovelStatus.UNKNOWN -> "Unknown"
        NovelStatus.ONGOING -> "Ongoing"
        NovelStatus.COMPLETED -> "Completed"
        NovelStatus.HIATUS -> "Hiatus"
        NovelStatus.CANCELLED -> "Cancelled"
    }

val NovelStatus.icon: ImageVector
    get() = when (this) {
        NovelStatus.UNKNOWN -> Icons.Default.HelpOutline
        NovelStatus.ONGOING -> Icons.Default.Schedule
        NovelStatus.COMPLETED -> Icons.Default.CheckCircle
        NovelStatus.HIATUS -> Icons.Default.PauseCircle
        NovelStatus.CANCELLED -> Icons.Default.Block
    }
