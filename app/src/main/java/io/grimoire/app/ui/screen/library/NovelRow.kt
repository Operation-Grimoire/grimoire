package io.grimoire.app.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelRow(
    novel: NovelEntity,
    stats: NovelChapterStats?,
    includeLockedInTotals: Boolean,
    showReadBadge: Boolean,
    showDownloadedBadge: Boolean,
    showLockedBadge: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else Color.Transparent
        )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            headlineContent = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = run {
                val visibility = resolveBadgeVisibility(
                    stats = stats,
                    includeLockedInTotals = includeLockedInTotals,
                    showReadBadge = showReadBadge,
                    showDownloadedBadge = showDownloadedBadge,
                    showLockedBadge = showLockedBadge,
                )
                when {
                    visibility.any && stats != null -> {
                        {
                            NovelStatsRowInline(
                                stats = stats,
                                includeLockedInTotals = includeLockedInTotals,
                                visibility = visibility,
                            )
                        }
                    }
                    !novel.author.isNullOrBlank() -> {
                        { Text(novel.author!!, maxLines = 1) }
                    }
                    else -> null
                }
            },
            leadingContent = {
                Box {
                    AsyncImage(
                        model = novel.thumbnailUrl,
                        contentDescription = novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        )
                    }
                }
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}
