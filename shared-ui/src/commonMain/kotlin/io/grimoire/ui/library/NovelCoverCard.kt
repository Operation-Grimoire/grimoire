package io.grimoire.ui.library

import io.grimoire.ui.library.*
import io.grimoire.ui.icon.*
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.theme.premiumGold

import io.grimoire.ui.resources.*

/** Library grid card: cover with status badges and the title below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelCoverCard(
    title: String,
    /** The cover image; it gets the card's 2:3 size and shape as its modifier. */
    cover: @Composable (Modifier) -> Unit,
    stats: ChapterCounts?,
    includeLockedInTotals: Boolean,
    showReadBadge: Boolean,
    showDownloadedBadge: Boolean,
    showLockedBadge: Boolean,
    showRatingBadge: Boolean,
    showEpubBadge: Boolean,
    isEpub: Boolean,
    notify: Boolean,
    autoDownload: Boolean,
    userRating: Int?,
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
                cover(
                    Modifier
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
                val showNotifyBadge = notify
                if (showNotifyBadge || autoDownload) {
                    NovelStatusBadgeOverlay(
                        showNotify = showNotifyBadge,
                        showAutoDownload = autoDownload,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    )
                }
                // Download / locked counts and the user rating share the top-right
                // column so the wide bottom progress badge (many chapters) never
                // collides with them.
                val ratingBadge = userRating?.takeIf { showRatingBadge }
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
                                    Res.string.library_locked_chapters,
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
                title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
