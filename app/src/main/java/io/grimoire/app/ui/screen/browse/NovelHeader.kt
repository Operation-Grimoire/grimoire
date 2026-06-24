package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.app.ui.component.ImageAction
import io.grimoire.app.ui.component.ShimmerBox
import io.grimoire.app.ui.component.ZoomableCoverImage
import io.grimoire.app.ui.component.displayName
import io.grimoire.app.ui.component.icon
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelHeader(
    novel: Novel,
    overrides: NovelOverrides,
    coverModel: Any?,
    sourceName: String = "",
    isLocal: Boolean = false,
    onEditField: (EditableField) -> Unit = {},
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
        add(ImageAction(AppIcons.Image, "Replace with image") {
            coverPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        })
        add(ImageAction(AppIcons.Link, "Replace with URL") { showCoverUrlDialog = true })
        if (hasCoverOverride) {
            add(ImageAction(AppIcons.Restore, "Reset to source cover") { onResetCover() })
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
                .width(140.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    novel.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .longPressToEdit(EditableField.TITLE, onEditField),
                )
                OverrideIndicator(overrides.title != null) { onEditField(EditableField.TITLE) }
            }
            if (!novel.author.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        novel.author!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .longPressToEdit(EditableField.AUTHOR, onEditField),
                    )
                    OverrideIndicator(overrides.author != null) { onEditField(EditableField.AUTHOR) }
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(
                        text = novel.status.displayName,
                        icon = novel.status.icon,
                        modifier = Modifier.longPressToEdit(EditableField.STATUS, onEditField),
                    )
                    OverrideIndicator(overrides.status != null) { onEditField(EditableField.STATUS) }
                }
                novel.rating?.let { rating ->
                    MetaChip(
                        text = buildString {
                            append(String.format(Locale.getDefault(), "%.1f", rating.coerceIn(0f, 5f)))
                            novel.ratingCount?.takeIf { it > 0 }?.let { append(" (").append(it).append(')') }
                        },
                        icon = AppIcons.Star,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { showRatingInfo = true },
                    )
                }
                if (isLocal) {
                    MetaChip(text = "EPUB", icon = AppIcons.Book)
                } else if (sourceName.isNotBlank()) {
                    MetaChip(text = sourceName, icon = AppIcons.ExtensionFilled)
                    val lang = novel.language
                        .takeIf { it != Language.UNKNOWN && it != Language.MULTI }?.displayName.orEmpty()
                    if (lang.isNotEmpty()) MetaChip(text = lang)
                }
            }
        }
    }
}

/**
 * Compact tonal chip for a single piece of novel metadata (status / rating / source /
 * language). Optional [icon] and [onClick]; clickable chips clip their ripple to the
 * chip shape. Kept header-local so the shared list/quick-view labels stay unchanged.
 */
@Composable
private fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Long-press a metadata field to open its single-field edit sheet (#152).
 * Uses [combinedClickable] so the press shows the standard ripple/darken feedback;
 * a plain tap is a no-op (editing is intentionally long-press only).
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.longPressToEdit(
    field: EditableField,
    onEdit: (EditableField) -> Unit,
): Modifier = composed {
    combinedClickable(
        onClick = {},
        onLongClick = { onEdit(field) },
    )
}

/**
 * Small pencil shown next to a metadata field that the user has overridden (#152).
 * Tapping it opens that field's edit sheet. Renders nothing when [overridden] is false.
 */
@Composable
internal fun OverrideIndicator(overridden: Boolean, onClick: () -> Unit) {
    if (!overridden) return
    IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
        Icon(
            AppIcons.EditOutlined,
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
        ShimmerBox(modifier = Modifier.width(140.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(8.dp))
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
