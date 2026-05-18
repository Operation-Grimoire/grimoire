package io.grimoire.app.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrolling row of genre chips, shared by the novel-detail and
 * NovelUpdates series screens so genres render consistently.
 */
@Composable
fun GenreChips(
    genres: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            AssistChip(
                onClick = {},
                label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
}
