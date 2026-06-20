package io.grimoire.app.ui.screen.browse

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.Novel
import io.grimoire.app.ui.component.ImageAction
import io.grimoire.app.ui.component.RatingLabel
import io.grimoire.app.ui.component.ShimmerBox
import io.grimoire.app.ui.component.StatusLabel
import io.grimoire.app.ui.component.ZoomableCoverImage

@Composable
internal fun NovelHeader(
    novel: Novel,
    overrides: NovelOverrides,
    coverModel: Any?,
    sourceName: String = "",
    isLocal: Boolean = false,
    onEditMetadata: () -> Unit = {},
    onSetCoverUri: (Uri) -> Unit = {},
    onSetCoverUrl: (String) -> Unit = {},
    onResetCover: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showRatingInfo by remember { mutableStateOf(false) }
    var showCoverUrlDialog by remember { mutableStateOf(false) }

    if (showRatingInfo) {
        AlertDialog(
            onDismissRequest = { showRatingInfo = false },
            title = { Text("About this rating") },
            text = {
                Text(
                    "This rating is reported by ${sourceName.ifBlank { "the source" }} " +
                        "and reflects readers there — not your activity in Grimoire.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showRatingInfo = false }) { Text("Got it") }
            },
        )
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onSetCoverUri) }

    if (showCoverUrlDialog) {
        var url by remember { mutableStateOf(overrides.coverUrl.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showCoverUrlDialog = false },
            title = { Text("Cover image URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = { onSetCoverUrl(url); showCoverUrlDialog = false },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCoverUrlDialog = false }) { Text("Cancel") }
            },
        )
    }

    val hasCoverOverride = overrides.coverPath != null || overrides.coverUrl != null
    val coverActions = buildList {
        add(ImageAction(Icons.Default.Image, "Replace with image") {
            coverPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        })
        add(ImageAction(Icons.Default.Link, "Replace with URL") { showCoverUrlDialog = true })
        if (hasCoverOverride) {
            add(ImageAction(Icons.Default.Restore, "Reset to source cover") { onResetCover() })
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ZoomableCoverImage(
            model = coverModel,
            contentDescription = novel.title,
            saveBaseName = novel.title,
            extraActions = coverActions,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    novel.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                OverrideIndicator(overrides.title != null, onEditMetadata)
            }
            if (!novel.author.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        novel.author!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    OverrideIndicator(overrides.author != null, onEditMetadata)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusLabel(status = novel.status)
                OverrideIndicator(overrides.status != null, onEditMetadata)
                novel.rating?.let {
                    RatingLabel(
                        rating = it,
                        count = novel.ratingCount,
                        onClick = { showRatingInfo = true },
                    )
                }
            }
            if (isLocal) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("EPUB") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            } else if (sourceName.isNotBlank()) {
                val lang = novel.language?.trim().orEmpty()
                Text(
                    if (lang.isNotEmpty()) "$sourceName · $lang" else sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Small pencil shown next to a metadata field that the user has overridden (#152).
 * Tapping it opens the edit sheet. Renders nothing when [overridden] is false.
 */
@Composable
internal fun OverrideIndicator(overridden: Boolean, onClick: () -> Unit) {
    if (!overridden) return
    IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Edited — tap to change",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun NovelHeaderSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBox(modifier = Modifier.width(120.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(8.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(20.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp))
        }
    }
}

@Composable
internal fun ChapterSkeletonItem(alpha: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(15.dp), alpha = alpha)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(11.dp), alpha = alpha)
        }
        ShimmerBox(modifier = Modifier.size(20.dp), shape = CircleShape, alpha = alpha)
    }
}
