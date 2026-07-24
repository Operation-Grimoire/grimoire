package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Prominent action row shown directly below [NovelHeader] (#bookmark-surfacing).
 * Replaces the easy-to-miss top-bar bookmark icon with labeled, Mihon-style actions
 * (icon over caption) spread evenly edge-to-edge so the row reads as intentional
 * rather than a sparse centered cluster. The WebView and category actions live here
 * too, leaving the top bar with just back / title / overflow.
 *
 * Order is category → library → WebView → rate. The category and rate buttons are present only
 * while the novel is in the library; their slots grow/shrink on a shared weight animation so
 * toggling the library reflows the row smoothly instead of snapping, and the library button
 * cross-fades its icon/label/tint between the add and in-library states.
 */
@Composable
internal fun NovelActionRow(
    inLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    showWebView: Boolean,
    onOpenWebView: () -> Unit,
    categoryName: String?,
    onEditCategory: () -> Unit,
    userRating: Int?,
    onRate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Retain the last name so the category button can animate out without its label
    // blanking mid-collapse (the screen passes null once the novel leaves the library).
    var lastCategoryName by remember { mutableStateOf(categoryName) }
    if (categoryName != null) lastCategoryName = categoryName

    // Drives both the slot width (weight) and a fade for the library-only buttons
    // (category + rate), so they grow in from their edge and shrink back out rather
    // than popping in/out.
    val inLibraryWeight by animateFloatAsState(
        targetValue = if (inLibrary) 1f else 0f,
        label = "inLibraryWeight",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inLibraryWeight > 0.001f) {
            NovelActionButton(
                modifier = Modifier.weight(inLibraryWeight),
                icon = AppIcons.Label,
                label = lastCategoryName ?: "—",
                active = false,
                onClick = onEditCategory,
                contentAlpha = inLibraryWeight.coerceIn(0f, 1f),
            )
        }
        NovelActionButton(
            modifier = Modifier.weight(1f),
            icon = if (inLibrary) AppIcons.Bookmark else AppIcons.BookmarkBorder,
            label = if (inLibrary) "In library" else "Add to library",
            active = inLibrary,
            onClick = onToggleLibrary,
        )
        if (showWebView) {
            NovelActionButton(
                modifier = Modifier.weight(1f),
                icon = AppIcons.Language,
                label = "WebView",
                active = false,
                onClick = onOpenWebView,
            )
        }
        // Rating is a library-only action: it grows/shrinks with the same weight as the
        // category button so the row reflows smoothly when the library is toggled.
        if (inLibraryWeight > 0.001f) {
            NovelActionButton(
                modifier = Modifier.weight(inLibraryWeight),
                icon = AppIcons.Star,
                label = if (userRating != null) "$userRating/10" else "Rate",
                active = userRating != null,
                onClick = onRate,
                contentAlpha = inLibraryWeight.coerceIn(0f, 1f),
            )
        }
    }
}

@Composable
private fun NovelActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
) {
    val tint by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "actionTint",
    )
    Column(
        modifier = modifier
            .graphicsLayer { alpha = contentAlpha }
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Crossfade(targetState = icon, label = "actionIcon") { current ->
            Icon(
                imageVector = current,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Crossfade(
            targetState = label,
            modifier = Modifier.fillMaxWidth(),
            label = "actionLabel",
        ) { current ->
            Text(
                text = current,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
