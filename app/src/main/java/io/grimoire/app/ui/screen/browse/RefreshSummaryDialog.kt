package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.app.ui.theme.premiumGold

@Composable
internal fun RefreshSummaryDialog(
    summary: RefreshSummary,
    onDismiss: () -> Unit,
) {
    val count = summary.chapters.size
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(if (count == 1) "1 new chapter" else "$count new chapters") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = summary.chapters.size,
                    key = { summary.chapters[it].let { ch -> "${ch.chapterNumber}-${ch.name}" } },
                ) { i ->
                    RefreshSummaryRow(summary.chapters[i])
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun RefreshSummaryRow(chapter: RefreshedChapter) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (chapter.locked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.premiumGold,
            )
        }
        if (chapter.unlockedFromLocked) {
            Text(
                text = "Unlocked",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.premiumGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.premiumGold.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            if (chapter.chapterNumber >= 0f) {
                val formatted = if (chapter.chapterNumber % 1f == 0f) {
                    chapter.chapterNumber.toInt().toString()
                } else {
                    chapter.chapterNumber.toString()
                }
                Text(
                    text = "Chapter $formatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
