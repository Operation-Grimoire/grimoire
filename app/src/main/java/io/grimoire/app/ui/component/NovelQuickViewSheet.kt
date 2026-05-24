package io.grimoire.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.ui.screen.browse.NovelQuickViewViewModel
import kotlinx.coroutines.launch

/**
 * Long-press preview that reuses the detail screen's load path, so opening
 * the full screen from here is instant. The caller owns navigation; sheet
 * just dismisses first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelQuickViewSheet(
    packageName: String,
    novelUrl: String,
    onOpenDetails: () -> Unit,
    onChapterClick: (chapterUrl: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val vm = hiltViewModel<NovelQuickViewViewModel, NovelQuickViewViewModel.Factory>(
        key = "quickview::$packageName::$novelUrl",
    ) { factory -> factory.create(packageName, novelUrl) }

    val novel by vm.novel.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isLoadingChapters by vm.isLoadingChapters.collectAsState()
    val error by vm.error.collectAsState()
    val isFavorite by vm.isFavorite.collectAsState()
    val latestChapters by vm.latestChapters.collectAsState()
    val chapterCount by vm.chapterCount.collectAsState()
    val categories by vm.categories.collectAsState()
    val categoryId by vm.categoryId.collectAsState()

    var chaptersExpanded by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    fun dismissAndRun(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            if (novel.title.isBlank() && isLoading) {
                Box(
                    Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                NovelQuickHero(
                    title = novel.title.ifBlank { "Loading…" },
                    author = novel.author,
                    sourceName = vm.sourceName,
                    thumbnailUrl = novel.thumbnailUrl,
                    status = novel.status,
                    rating = novel.rating,
                    ratingCount = novel.ratingCount,
                )

                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                ActionRow(
                    isFavorite = isFavorite,
                    canToggleLibrary = !isLoading,
                    onToggleFavorite = vm::toggleFavorite,
                    onPickCategory = { showCategorySheet = true },
                    onOpen = { dismissAndRun(onOpenDetails) },
                )

                if (novel.genres.isNotEmpty()) {
                    GenreChips(
                        genres = novel.genres,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (!novel.description.isNullOrBlank()) {
                    ExpandableText(
                        text = novel.description!!,
                        collapsedMaxLines = 4,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (chapterCount > 0 || isLoadingChapters) {
                    ChapterSection(
                        chapterCount = chapterCount,
                        latestChapters = latestChapters,
                        expanded = chaptersExpanded,
                        loading = isLoadingChapters,
                        onToggle = { chaptersExpanded = !chaptersExpanded },
                        onChapterClick = { chapter ->
                            dismissAndRun { onChapterClick(chapter.url) }
                        },
                        onDownload = vm::downloadChapter,
                        onCancelDownload = vm::cancelDownload,
                        onDeleteDownload = vm::deleteDownload,
                        onRedownload = vm::redownloadChapter,
                        // Locked rows route through full detail so its unlock dialog handles them.
                        onLockedClick = { dismissAndRun(onOpenDetails) },
                    )
                }
            }
        }
    }

    if (showCategorySheet && categories.isNotEmpty()) {
        MoveToCategorySheet(
            categories = categories,
            count = 1,
            onSelect = { target ->
                vm.setCategory(target)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
            currentCategoryId = categoryId,
            showCurrent = true,
        )
    }
}

@Composable
private fun NovelQuickHero(
    title: String,
    author: String?,
    sourceName: String,
    thumbnailUrl: String?,
    status: NovelStatus,
    rating: Float?,
    ratingCount: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ZoomableCoverImage(
            model = thumbnailUrl,
            contentDescription = title,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
            if (!author.isNullOrBlank()) {
                Text(
                    author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sourceName.isNotBlank()) {
                Text(
                    sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                StatusLabel(status = status)
                rating?.let {
                    RatingLabel(rating = it, count = ratingCount, onClick = {})
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(
    isFavorite: Boolean,
    canToggleLibrary: Boolean,
    onToggleFavorite: () -> Unit,
    onPickCategory: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconToggleButton(
            checked = isFavorite,
            onCheckedChange = { if (canToggleLibrary) onToggleFavorite() },
            enabled = canToggleLibrary,
        ) {
            Icon(
                if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isFavorite) "Remove from library" else "Add to library",
            )
        }
        IconButton(onClick = onPickCategory, enabled = canToggleLibrary) {
            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Change category")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onOpen) { Text("Open") }
    }
}

@Composable
private fun ChapterSection(
    chapterCount: Int,
    latestChapters: List<ChapterEntity>,
    expanded: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onChapterClick: (ChapterEntity) -> Unit,
    onDownload: (ChapterEntity) -> Unit,
    onCancelDownload: (ChapterEntity) -> Unit,
    onDeleteDownload: (ChapterEntity) -> Unit,
    onRedownload: (ChapterEntity) -> Unit,
    onLockedClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    loading && chapterCount == 0 -> "Loading chapters…"
                    chapterCount == 1 -> "1 chapter"
                    else -> "$chapterCount chapters"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (loading && chapterCount == 0) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (chapterCount > 0) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            )
            latestChapters.forEach { chapter ->
                ChapterItem(
                    chapter = chapter,
                    selected = false,
                    selectionMode = false,
                    onClick = { onChapterClick(chapter) },
                    onLockedClick = onLockedClick,
                    onToggleSelection = {},
                    onDownload = { onDownload(chapter) },
                    onCancelDownload = { onCancelDownload(chapter) },
                    onDeleteDownload = { onDeleteDownload(chapter) },
                    onRedownload = { onRedownload(chapter) },
                )
            }
        }
    }
}
