package io.grimoire.app.ui.screen.library

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.effectiveTotal
import io.grimoire.app.data.local.entity.readPercent
import io.grimoire.app.ui.theme.premiumGold

internal data class NovelBadgeVisibility(
    val showRead: Boolean,
    val showDownloaded: Boolean,
    val showLocked: Boolean,
) {
    val any: Boolean get() = showRead || showDownloaded || showLocked
}

internal fun resolveBadgeVisibility(
    stats: NovelChapterStats?,
    includeLockedInTotals: Boolean,
    showReadBadge: Boolean,
    showDownloadedBadge: Boolean,
    showLockedBadge: Boolean,
): NovelBadgeVisibility {
    if (stats == null) return NovelBadgeVisibility(false, false, false)
    return NovelBadgeVisibility(
        showRead = showReadBadge && stats.effectiveTotal(includeLockedInTotals) > 0,
        showDownloaded = showDownloadedBadge && stats.downloadedCount > 0,
        showLocked = showLockedBadge && stats.lockedCount > 0,
    )
}

@Composable
internal fun NovelStatsRowInline(
    stats: NovelChapterStats,
    includeLockedInTotals: Boolean,
    visibility: NovelBadgeVisibility,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (visibility.showRead) {
            val displayedTotal = stats.effectiveTotal(includeLockedInTotals)
            val percent = stats.readPercent(includeLockedInTotals)
            InlineBadge(
                icon = AppIcons.CheckCircle,
                contentDescription = null,
                text = "${stats.readCount}/$displayedTotal ($percent%)",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (visibility.showDownloaded) {
            InlineBadge(
                icon = AppIcons.Download,
                contentDescription = null,
                text = "${stats.downloadedCount}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (visibility.showLocked) {
            InlineBadge(
                icon = AppIcons.Lock,
                contentDescription = stringResource(R.string.library_locked_chapters),
                text = "${stats.lockedCount}",
                tint = MaterialTheme.colorScheme.premiumGold,
            )
        }
    }
}

@Composable
private fun InlineBadge(
    icon: ImageVector,
    contentDescription: String?,
    text: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun NovelReadBadgeOverlay(
    stats: NovelChapterStats,
    includeLockedInTotals: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayedTotal = stats.effectiveTotal(includeLockedInTotals)
    val percent = stats.readPercent(includeLockedInTotals)
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "${stats.readCount}/$displayedTotal",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
internal fun NovelCountBadgeOverlay(
    count: Int,
    icon: ImageVector,
    iconContentDescription: String?,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            icon,
            contentDescription = iconContentDescription,
            tint = iconTint,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Cover overlay that flags an imported-EPUB (local) novel so it reads as distinct
 * from extension-backed novels at a glance. Rendered top-left; callers gate it on
 * the novel being local and on the user's "Show EPUB badge" preference.
 */
@Composable
internal fun NovelEpubBadgeOverlay(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            AppIcons.MenuBook,
            contentDescription = stringResource(R.string.library_epub_badge),
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = stringResource(R.string.library_epub_badge),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Cover overlay that flags the per-novel "new chapter" settings — a bell when
 * notifications are on, an auto-download glyph when auto-download is on. Distinct
 * from [NovelCountBadgeOverlay] (which counts chapters) so the static download
 * count and the auto-download flag never read as the same thing. Callers should
 * only render this when at least one flag is set.
 */
@Composable
internal fun NovelStatusBadgeOverlay(
    showNotify: Boolean,
    showAutoDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (showNotify) {
            Icon(
                AppIcons.Notifications,
                contentDescription = stringResource(R.string.library_notifications_enabled),
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
        if (showAutoDownload) {
            Icon(
                AppIcons.CloudDownload,
                contentDescription = stringResource(R.string.library_auto_download_enabled),
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
