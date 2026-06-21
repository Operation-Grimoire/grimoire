package io.grimoire.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrolling row of genre chips, shared by the novel-detail and
 * NovelUpdates series screens so genres render consistently.
 *
 * Each chip is single-clickable via [onGenreClick] (reserved for a future
 * genre-search/filter feature). [onLongPress] fires on a long press of a chip —
 * used on the novel-detail screen to edit the genres override. Both live on the
 * per-chip [combinedClickable] so tap and long-press coexist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenreChips(
    genres: List<String>,
    modifier: Modifier = Modifier,
    onGenreClick: ((String) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .height(28.dp)
                    .combinedClickable(
                        onClick = { onGenreClick?.invoke(genre) },
                        onLongClick = onLongPress,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(genre, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
