package io.grimoire.app.ui.screen.library

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.app.R
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.ui.theme.premiumGold

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelCard(
    novel: NovelEntity,
    stats: NovelChapterStats?,
    includeLockedInTotals: Boolean,
    showReadBadge: Boolean,
    showDownloadedBadge: Boolean,
    showLockedBadge: Boolean,
    showRatingBadge: Boolean,
    showEpubBadge: Boolean,
    isEpub: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Column(
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box {
                AsyncImage(
                    model = novel.effectiveCoverModel(),
                    contentDescription = novel.effectiveTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (selected) Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                            ) else Modifier
                        ),
                )
                if (selected) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                }
                val visibility = resolveBadgeVisibility(
                    stats = stats,
                    includeLockedInTotals = includeLockedInTotals,
                    showReadBadge = showReadBadge,
                    showDownloadedBadge = showDownloadedBadge,
                    showLockedBadge = showLockedBadge,
                )
                if (visibility.showRead && stats != null) {
                    NovelReadBadgeOverlay(
                        stats = stats,
                        includeLockedInTotals = includeLockedInTotals,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                    )
                }
                // EPUB novels can't have notification / auto-download flags, so the
                // EPUB badge and the status badge never compete for the top-left corner.
                if (showEpubBadge && isEpub) {
                    NovelEpubBadgeOverlay(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    )
                }
                val showNotifyBadge = novel.notifyOnNewChapters || novel.notifyOnNewLockedChapters
                if (showNotifyBadge || novel.autoDownloadNewChapters) {
                    NovelStatusBadgeOverlay(
                        showNotify = showNotifyBadge,
                        showAutoDownload = novel.autoDownloadNewChapters,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    )
                }
                // Download / locked counts and the user rating share the top-right
                // column so the wide bottom progress badge (many chapters) never
                // collides with them.
                val ratingBadge = novel.userRating?.takeIf { showRatingBadge }
                val showCounts = stats != null && (visibility.showDownloaded || visibility.showLocked)
                if (showCounts || ratingBadge != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (stats != null && visibility.showDownloaded) {
                            NovelCountBadgeOverlay(
                                count = stats.downloadedCount,
                                icon = AppIcons.Download,
                                iconContentDescription = null,
                                iconTint = Color.White,
                            )
                        }
                        if (stats != null && visibility.showLocked) {
                            NovelCountBadgeOverlay(
                                count = stats.lockedCount,
                                icon = AppIcons.Lock,
                                iconContentDescription = stringResource(
                                    R.string.library_locked_chapters,
                                ),
                                iconTint = MaterialTheme.colorScheme.premiumGold,
                            )
                        }
                        if (ratingBadge != null) {
                            NovelRatingBadgeOverlay(rating = ratingBadge)
                        }
                    }
                }
            }
            Text(
                novel.effectiveTitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
