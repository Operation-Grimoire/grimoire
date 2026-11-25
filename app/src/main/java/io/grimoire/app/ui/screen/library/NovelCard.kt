package io.grimoire.app.ui.screen.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.counts
import io.grimoire.ui.library.NovelCoverCard

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
) = NovelCoverCard(
    title = novel.effectiveTitle,
    cover = { coverModifier ->
        AsyncImage(
            model = novel.effectiveCoverModel(),
            contentDescription = novel.effectiveTitle,
            contentScale = ContentScale.Crop,
            modifier = coverModifier,
        )
    },
    stats = stats?.counts(),
    includeLockedInTotals = includeLockedInTotals,
    showReadBadge = showReadBadge,
    showDownloadedBadge = showDownloadedBadge,
    showLockedBadge = showLockedBadge,
    showRatingBadge = showRatingBadge,
    showEpubBadge = showEpubBadge,
    isEpub = isEpub,
    notify = novel.notifyOnNewChapters || novel.notifyOnNewLockedChapters,
    autoDownload = novel.autoDownloadNewChapters,
    userRating = novel.userRating,
    selected = selected,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
)
