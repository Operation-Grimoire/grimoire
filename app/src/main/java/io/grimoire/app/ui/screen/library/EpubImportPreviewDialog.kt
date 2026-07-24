package io.grimoire.app.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.app.R
import io.grimoire.app.data.epub.StagedEpub
import java.nio.ByteBuffer

@Composable
internal fun EpubImportPreviewDialog(
    staged: StagedEpub,
    importing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val author = staged.author?.takeIf { it.isNotBlank() }
    val description = staged.description?.takeIf { it.isNotBlank() }
    val coverBytes = staged.coverBytes
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(R.string.library_import_epub)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (coverBytes != null) {
                        AsyncImage(
                            model = ByteBuffer.wrap(coverBytes),
                            contentDescription = staged.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(staged.title, style = MaterialTheme.typography.titleMedium)
                        if (author != null) {
                            Text(
                                author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            pluralStringResource(
                                R.plurals.library_chapter_count,
                                staged.chapterCount,
                                staged.chapterCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (staged.genres.isNotEmpty()) {
                            Text(
                                staged.genres.joinToString(
                                    separator = stringResource(R.string.library_genre_separator),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !importing) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.library_add_to_library))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
