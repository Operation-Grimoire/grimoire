package io.grimoire.ui.novel

import io.grimoire.ui.icon.*
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
import org.jetbrains.compose.resources.stringResource
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.ui.component.localizedDisplayName
import io.grimoire.ui.component.icon
import java.util.Locale

import io.grimoire.ui.resources.*

/** Header fields a platform can let the user edit. */
enum class HeaderField { TITLE, AUTHOR, STATUS }

/**
 * Novel details header: cover, title, author and status, rating, source and language chips.
 * The cover is a platform slot; [onEditField] enables long-press editing where supported.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelHeaderContent(
    novel: Novel,
    cover: @Composable (Modifier) -> Unit,
    sourceName: String = "",
    isLocal: Boolean = false,
    sourceMissing: Boolean = false,
    missingSourceName: String? = null,
    onSourceMissingClick: () -> Unit = {},
    onRatingClick: () -> Unit = {},
    overridden: (HeaderField) -> Boolean = { false },
    onEditField: ((HeaderField) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        cover(
            Modifier
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
                        .longPressToEdit(HeaderField.TITLE, onEditField),
                )
                OverrideIndicator(overridden(HeaderField.TITLE)) { onEditField?.invoke(HeaderField.TITLE) }
            }
            if (!novel.author.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        novel.author!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .longPressToEdit(HeaderField.AUTHOR, onEditField),
                    )
                    OverrideIndicator(overridden(HeaderField.AUTHOR)) { onEditField?.invoke(HeaderField.AUTHOR) }
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(
                        text = novel.status.localizedDisplayName(),
                        icon = novel.status.icon,
                        modifier = Modifier.longPressToEdit(HeaderField.STATUS, onEditField),
                    )
                    OverrideIndicator(overridden(HeaderField.STATUS)) { onEditField?.invoke(HeaderField.STATUS) }
                }
                novel.rating?.let { rating ->
                    MetaChip(
                        text = buildString {
                            append(String.format(Locale.getDefault(), "%.1f", rating.coerceIn(0f, 5f)))
                            novel.ratingCount?.takeIf { it > 0 }?.let { append(" (").append(it).append(')') }
                        },
                        icon = AppIcons.Star,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onRatingClick,
                    )
                }
                if (sourceMissing) {
                    // Extension gone: name the source when the repo index still knows it,
                    // so it's clear which one to reinstall.
                    MetaChip(
                        text = missingSourceName
                            ?.let { stringResource(Res.string.novel_source_uninstalled_named, it) }
                            ?: stringResource(Res.string.novel_source_uninstalled),
                        icon = AppIcons.WarningAmber,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = onSourceMissingClick,
                    )
                } else if (isLocal) {
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
fun MetaChip(
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

/** Long-press a metadata field to edit it; a plain tap does nothing. No effect without [onEdit]. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.longPressToEdit(field: HeaderField, onEdit: ((HeaderField) -> Unit)?): Modifier =
    if (onEdit == null) this else composed { combinedClickable(onClick = {}, onLongClick = { onEdit(field) }) }

/**
 * Small pencil shown next to a metadata field that the user has overridden (#152).
 * Tapping it opens that field's edit sheet. Renders nothing when [overridden] is false.
 */
@Composable
fun OverrideIndicator(overridden: Boolean, onClick: () -> Unit) {
    if (!overridden) return
    IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
        Icon(
            AppIcons.EditOutlined,
            contentDescription = stringResource(Res.string.novel_metadata_edited),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

