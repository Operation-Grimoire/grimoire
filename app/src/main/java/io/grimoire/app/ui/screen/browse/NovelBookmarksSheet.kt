package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.local.entity.BookmarkEntity
import io.grimoire.app.ui.screen.reader.bookmarkColor

/**
 * Per-novel list of in-chapter bookmarks (#132), surfaced from the novel-detail
 * screen. Each row shows its colour, chapter, and the saved/highlighted text. Tap
 * to open the reader at that position; delete with the trailing X.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelBookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    onOpen: (BookmarkEntity) -> Unit,
    onDelete: (id: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = if (bookmarks.isEmpty()) "Bookmarks" else "${bookmarks.size} bookmarks",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (bookmarks.isEmpty()) {
                Text(
                    "No bookmarks yet. While reading, tap the bookmark icon, then tap the " +
                        "text to drop a marker or drag to highlight.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(bookmark) }
                                .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(bookmarkColor(bookmark.colorIndex)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookmark.chapterName.ifBlank { "Chapter" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = bookmark.note?.takeIf { it.isNotBlank() }
                                    ?: bookmark.text.takeIf { it.isNotBlank() }
                                    ?: if (bookmark.isHighlight) "Highlight" else "Marker"
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDelete(bookmark.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete bookmark")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
