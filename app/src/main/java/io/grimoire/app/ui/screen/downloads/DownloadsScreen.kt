package io.grimoire.app.ui.screen.downloads

import androidx.activity.compose.BackHandler
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.TooltipIconButton

/**
 * Each chip in the downloads filter row represents a logical "category" of download
 * statuses (downloading, queued, …). Storing the selection as a Set<DownloadStatusFilter>
 * lets the user pick any combination — the actual ordinal predicate the list filters
 * on is the union of every selected category's [ordinals].
 */
private enum class DownloadStatusFilter(val label: String, val ordinals: Set<Int>) {
    DOWNLOADING("Downloading", ChapterDownloadStatus.DOWNLOADING_ORDINALS),
    QUEUED("Queued", ChapterDownloadStatus.QUEUED_ORDINALS),
    DONE("Done", setOf(ChapterDownloadStatus.DOWNLOADED.ordinal)),
    FAILED("Failed", ChapterDownloadStatus.ERROR_ORDINALS),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val downloads by viewModel.downloads.collectAsState()
    val currentDownloads = downloads
    val isPaused by viewModel.isPaused.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    var expandedNovels by remember { mutableStateOf(setOf<Long>()) }
    var selectedStatusFilters by remember { mutableStateOf(emptySet<DownloadStatusFilter>()) }
    // Empty selection collapses to null (no restriction) so downstream code keeps the
    // same null-vs-non-null shape it had before this was multi-select.
    val statusFilter: Set<Int>? = selectedStatusFilters
        .takeIf { it.isNotEmpty() }
        ?.flatMapTo(mutableSetOf()) { it.ordinals }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var selectedChapterIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedChapterIds.isNotEmpty()
    val clearSelection: () -> Unit = { selectedChapterIds = emptySet() }
    val toggleChapter: (Long) -> Unit = { id ->
        selectedChapterIds = if (id in selectedChapterIds) selectedChapterIds - id else selectedChapterIds + id
    }
    // Header tap toggles every visible (filtered) chapter under the novel:
    // if all of them are already selected the whole novel deselects, otherwise
    // any unselected ones are added. The header's "selected" highlight is then
    // derived from "all my visible chapters are selected".
    val toggleNovelChapters: (List<Long>) -> Unit = { chapterIds ->
        selectedChapterIds = if (chapterIds.all { it in selectedChapterIds }) {
            selectedChapterIds - chapterIds.toSet()
        } else {
            selectedChapterIds + chapterIds
        }
    }

    BackHandler(enabled = selectionMode) { clearSelection() }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Download settings", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Parallel downloads", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Number of chapters downloaded at once",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlainTooltipIconButton(
                            onClick = { viewModel.setConcurrency(concurrency - 1) },
                            enabled = concurrency > 1, tooltip = "Decrease") {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text(
                            text = concurrency.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center,
                        )
                        PlainTooltipIconButton(
                            onClick = { viewModel.setConcurrency(concurrency + 1) },
                            enabled = concurrency < 5, tooltip = "Increase") {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedChapterIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        val visibleChapterIds = (currentDownloads ?: emptyList())
                            .flatMap { nd ->
                                val f = statusFilter
                                if (f == null) nd.chapters
                                else nd.chapters.filter { it.downloadStatus in f }
                            }
                            .map { it.id }
                            .toSet()
                        selectedChapterIds = if (selectedChapterIds.containsAll(visibleChapterIds)) {
                            emptySet()
                        } else {
                            visibleChapterIds
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Downloads") },
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!currentDownloads.isNullOrEmpty()) {
                            PlainTooltipIconButton(onClick = viewModel::togglePause, tooltip = if (isPaused) "Resume" else "Pause") {
                                Icon(
                                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isPaused) "Resume" else "Pause",
                                )
                            }
                        }
                        PlainTooltipIconButton(onClick = { showSettings = true }, tooltip = "Settings") {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            TooltipBottomBar(visible = selectionMode) {
                val target = resolveTargets(currentDownloads, selectedChapterIds)
                val hasQueued = target.chapters.any { it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS }
                val hasInFlight = target.chapters.any { it.downloadStatus in ChapterDownloadStatus.IN_FLIGHT_ORDINALS }
                val hasError = target.chapters.any { it.downloadStatus in ChapterDownloadStatus.ERROR_ORDINALS }
                val hasDownloaded = target.chapters.any { it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal }
                val hasRedownloadable = target.chapters.any {
                    !it.locked && (
                        it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal ||
                            it.downloadStatus == ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal
                        )
                }

                TooltipIconButton(
                    visible = hasQueued,
                    icon = Icons.Default.KeyboardDoubleArrowUp,
                    label = "Move to top",
                    onClick = {
                        target.novelIds.forEach { viewModel.moveToTopOfQueue(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasInFlight,
                    icon = Icons.Default.Close,
                    label = "Cancel",
                    onClick = {
                        target.chapters
                            .filter { it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS }
                            .forEach { viewModel.cancel(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasError,
                    icon = Icons.Default.Refresh,
                    label = "Retry",
                    onClick = {
                        target.chapters
                            .filter { it.downloadStatus in ChapterDownloadStatus.ERROR_ORDINALS }
                            .forEach { viewModel.retryChapter(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasError,
                    icon = Icons.Default.Close,
                    label = "Cancel failed",
                    onClick = {
                        target.novelIds.forEach { viewModel.cancelAllFailed(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasRedownloadable,
                    icon = Icons.Default.Refresh,
                    label = "Redownload",
                    onClick = {
                        val redownloadable = target.chapters.filter {
                            !it.locked && (
                                it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal ||
                                    it.downloadStatus == ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal
                                )
                        }
                        viewModel.redownloadChapters(redownloadable)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasDownloaded,
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    onClick = {
                        target.chapters
                            .filter { it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal }
                            .forEach { viewModel.deleteDownload(it) }
                        clearSelection()
                    },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) { padding ->
        when {
            currentDownloads == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            currentDownloads.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No downloads", style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    // FlowRow wraps chips onto multiple lines so the full set of
                    // categories stays visible without horizontal scrolling, and
                    // tapping a chip toggles it in or out of the selection set.
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // "All" is its own chip that clears the selection rather
                        // than toggling a value — guarantees the unfiltered state
                        // is always one tap away regardless of what else is on.
                        FilterChip(
                            selected = selectedStatusFilters.isEmpty(),
                            onClick = { selectedStatusFilters = emptySet() },
                            label = { Text("All") },
                        )
                        DownloadStatusFilter.entries.forEach { chip ->
                            FilterChip(
                                selected = chip in selectedStatusFilters,
                                onClick = {
                                    selectedStatusFilters = if (chip in selectedStatusFilters) {
                                        selectedStatusFilters - chip
                                    } else {
                                        selectedStatusFilters + chip
                                    }
                                },
                                label = { Text(chip.label) },
                            )
                        }
                    }
                }

                currentDownloads.forEach { novelDownloads ->
                    val activeFilter = statusFilter
                    val filtered = if (activeFilter == null) novelDownloads.chapters
                        else novelDownloads.chapters.filter { it.downloadStatus in activeFilter }
                    if (filtered.isEmpty()) return@forEach

                    val novelId = novelDownloads.novel.id
                    val isCollapsed = novelId !in expandedNovels

                    item(key = "header_$novelId") {
                        val toggleCollapse = {
                            expandedNovels = if (isCollapsed) {
                                expandedNovels + novelId
                            } else {
                                expandedNovels - novelId
                            }
                        }
                        val filteredIds = filtered.map { it.id }
                        NovelDownloadHeader(
                            novelDownloads = novelDownloads,
                            collapsed = isCollapsed,
                            selected = filteredIds.all { it in selectedChapterIds },
                            onClick = {
                                if (selectionMode) toggleNovelChapters(filteredIds)
                                else toggleCollapse()
                            },
                            onLongClick = {
                                if (selectionMode) toggleCollapse()
                                else toggleNovelChapters(filteredIds)
                            },
                            onToggleCollapse = toggleCollapse,
                        )
                    }

                    if (!isCollapsed) {
                        items(items = filtered, key = { it.id }) { chapter ->
                            ChapterDownloadItem(
                                chapter = chapter,
                                selected = chapter.id in selectedChapterIds,
                                selectionMode = selectionMode,
                                onClick = { if (selectionMode) toggleChapter(chapter.id) },
                                onLongClick = { toggleChapter(chapter.id) },
                                onCancel = { viewModel.cancel(chapter) },
                                onRetry = { viewModel.retryChapter(chapter) },
                                onDelete = { viewModel.deleteDownload(chapter) },
                                onRedownload = { viewModel.redownloadChapter(chapter) },
                            )
                        }
                    }

                    item(key = "divider_$novelId") {
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * The chapters an action should touch, plus the set of novels they belong to —
 * needed for novel-scoped operations like "Cancel all failed" and "Move to top".
 */
private data class SelectionTargets(
    val novelIds: Set<Long>,
    val chapters: List<ChapterEntity>,
)

private fun resolveTargets(
    downloads: List<NovelDownloads>?,
    selectedChapterIds: Set<Long>,
): SelectionTargets {
    if (downloads == null) return SelectionTargets(emptySet(), emptyList())
    val chapters = mutableListOf<ChapterEntity>()
    val novels = mutableSetOf<Long>()
    downloads.forEach { nd ->
        nd.chapters.forEach { ch ->
            if (ch.id in selectedChapterIds) {
                chapters.add(ch)
                novels.add(nd.novel.id)
            }
        }
    }
    return SelectionTargets(novels, chapters)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelDownloadHeader(
    novelDownloads: NovelDownloads,
    collapsed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCollapse: () -> Unit,
) {
    val counts = remember(novelDownloads.chapters) {
        var downloaded = 0; var queued = 0; var downloading = 0; var error = 0
        for (c in novelDownloads.chapters) when (c.downloadStatus) {
            ChapterDownloadStatus.DOWNLOADED.ordinal -> downloaded++
            ChapterDownloadStatus.QUEUED.ordinal,
            ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal -> queued++
            ChapterDownloadStatus.DOWNLOADING.ordinal,
            ChapterDownloadStatus.REDOWNLOADING.ordinal -> downloading++
            ChapterDownloadStatus.ERROR.ordinal,
            ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal -> error++
        }
        intArrayOf(downloaded, queued, downloading, error)
    }
    val downloaded = counts[0]
    val queued = counts[1]
    val downloading = counts[2]
    val error = counts[3]

    val stats = remember(downloaded, queued, downloading, error) {
        buildList {
            if (downloaded > 0) add("$downloaded downloaded")
            if (queued > 0) add("$queued queued")
            if (downloading > 0) add("$downloading downloading")
            if (error > 0) add("$error failed")
        }.joinToString(" • ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = novelDownloads.novel.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = novelDownloads.novel.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stats,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlainTooltipIconButton(onClick = onToggleCollapse, tooltip = if (collapsed) "Expand" else "Collapse") {
            Icon(
                if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChapterDownloadItem(
    chapter: ChapterEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onRedownload: () -> Unit,
) {
    val status = ChapterDownloadStatus.entries.getOrElse(chapter.downloadStatus) { ChapterDownloadStatus.NONE }

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        } else ListItemDefaults.colors(),
        headlineContent = {
            Text(chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            when (status) {
                ChapterDownloadStatus.QUEUED,
                ChapterDownloadStatus.REDOWNLOAD_QUEUED -> Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = "Queued",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.DOWNLOADING,
                ChapterDownloadStatus.REDOWNLOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                ChapterDownloadStatus.DOWNLOADED -> Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.ERROR,
                ChapterDownloadStatus.REDOWNLOAD_ERROR -> Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.NONE -> Spacer(modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = if (selectionMode) {
            null
        } else {
            {
                when (status) {
                    ChapterDownloadStatus.QUEUED,
                    ChapterDownloadStatus.REDOWNLOAD_QUEUED -> PlainTooltipIconButton(onClick = onCancel, tooltip = "Cancel") {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    ChapterDownloadStatus.ERROR,
                    ChapterDownloadStatus.REDOWNLOAD_ERROR -> PlainTooltipIconButton(onClick = onRetry, tooltip = "Retry") {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                    ChapterDownloadStatus.DOWNLOADED -> {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = "Download options") {
                                Icon(Icons.Default.MoreVert, contentDescription = "Download options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Redownload") },
                                    onClick = {
                                        menuExpanded = false
                                        onRedownload()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete download") },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                    else -> Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
    )
}
