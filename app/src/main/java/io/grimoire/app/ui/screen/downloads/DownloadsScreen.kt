package io.grimoire.app.ui.screen.downloads

import io.grimoire.app.ui.icon.*
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    // `downloads` is already grouped, sorted, and narrowed to the active filter off the
    // main thread by the ViewModel — the screen renders sections as-is, no per-frame work.
    val uiState by viewModel.downloads.collectAsState()
    val currentDownloads = uiState?.novels
    val selectedStatusFilters by viewModel.activeStatusFilters.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    var expandedNovels by remember { mutableStateOf(setOf<Long>()) }
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
                Text(stringResource(R.string.downloads_settings), style = MaterialTheme.typography.titleMedium)
                io.grimoire.app.ui.component.sheet.StepperRow(
                    label = stringResource(R.string.downloads_parallel),
                    hint = stringResource(R.string.downloads_parallel_summary),
                    value = concurrency.toString(),
                    onDecrement = { viewModel.setConcurrency(concurrency - 1) },
                    onIncrement = { viewModel.setConcurrency(concurrency + 1) },
                    decrementEnabled = concurrency > 1,
                    incrementEnabled = concurrency < 5,
                )
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
                            .flatMap { nd -> nd.chapters }
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
                    title = { Text(stringResource(R.string.downloads_title)) },
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                            Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (!currentDownloads.isNullOrEmpty()) {
                            val pauseLabel = stringResource(if (isPaused) R.string.action_resume else R.string.action_pause)
                            PlainTooltipIconButton(onClick = viewModel::togglePause, tooltip = pauseLabel) {
                                Icon(
                                    if (isPaused) AppIcons.PlayArrow else AppIcons.Pause,
                                    contentDescription = pauseLabel,
                                )
                            }
                        }
                        PlainTooltipIconButton(onClick = { showSettings = true }, tooltip = stringResource(R.string.action_settings)) {
                            Icon(AppIcons.Settings, contentDescription = stringResource(R.string.action_settings))
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
                    icon = AppIcons.KeyboardDoubleArrowUp,
                    label = stringResource(R.string.downloads_move_to_top),
                    onClick = {
                        target.novelIds.forEach { viewModel.moveToTopOfQueue(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasInFlight,
                    icon = AppIcons.Close,
                    label = stringResource(R.string.action_cancel),
                    onClick = {
                        target.chapters
                            .filter { it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS }
                            .forEach { viewModel.cancel(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasError,
                    icon = AppIcons.Refresh,
                    label = stringResource(R.string.action_retry),
                    onClick = {
                        target.chapters
                            .filter { it.downloadStatus in ChapterDownloadStatus.ERROR_ORDINALS }
                            .forEach { viewModel.retryChapter(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasError,
                    icon = AppIcons.Close,
                    label = stringResource(R.string.downloads_cancel_failed),
                    onClick = {
                        target.novelIds.forEach { viewModel.cancelAllFailed(it) }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = hasRedownloadable,
                    icon = AppIcons.Refresh,
                    label = stringResource(R.string.downloads_redownload),
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
                    icon = AppIcons.Delete,
                    label = stringResource(R.string.action_delete),
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
        val state = uiState
        when {
            state == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            // Nothing downloading or downloaded at all — no filter to show.
            !state.hasAnyDownloads -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // The filter stays visible even when the active selection matches
                // nothing — hiding it left no way to un-filter (#317). Segments
                // are icon + live count (text labels with counts overflow four
                // segments); zero-count segments disable unless they're the
                // selected filter that still needs un-toggling.
                StatusFilterSegments(
                    counts = state.statusCounts,
                    selected = selectedStatusFilters,
                    onToggle = viewModel::toggleStatusFilter,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (state.novels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.downloads_no_filter_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else LazyColumn(modifier = Modifier.fillMaxSize()) {

                state.novels.forEach { novelDownloads ->
                    // Already filtered by the ViewModel — novels with no matching chapters
                    // were dropped, so chapters is non-empty here.
                    val visibleChapters = novelDownloads.chapters
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
                        val filteredIds = visibleChapters.map { it.id }
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
                        items(items = visibleChapters, key = { it.id }) { chapter ->
                            ChildRail {
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
                    }

                    item(key = "divider_$novelId") {
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                }
                }
            }
        }
    }
}

/**
 * Wraps a chapter row belonging to an expanded novel group, drawing a vertical
 * rail down its left edge so the nesting under the group header reads at a
 * glance — the same idiom as the updates page. The rail is centered under the
 * header's 48dp cover (16dp padding + 24dp half-cover = 40dp).
 */
@Composable
private fun ChildRail(content: @Composable () -> Unit) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = 40.dp.toPx()
                    drawLine(
                        color = railColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                },
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/**
 * Icon + count filter segments over the download statuses. Icons match the
 * per-chapter status icons below so the mapping reads instantly (failed is
 * error-tinted); the count is the live unfiltered tally for that status.
 */
@Composable
private fun StatusFilterSegments(
    counts: Map<DownloadStatusFilter, Int>,
    selected: Set<DownloadStatusFilter>,
    onToggle: (DownloadStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val entries = DownloadStatusFilter.entries
        entries.forEachIndexed { index, filter ->
            val count = counts[filter] ?: 0
            SegmentedButton(
                checked = filter in selected,
                onCheckedChange = { onToggle(filter) },
                enabled = count > 0 || filter in selected,
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = entries.size,
                ),
                // Suppress the default checkmark: the checked container color
                // already signals selection and the row has no width to spare.
                icon = {},
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (filter) {
                                DownloadStatusFilter.DOWNLOADING -> AppIcons.Download
                                DownloadStatusFilter.QUEUED -> AppIcons.HourglassEmpty
                                DownloadStatusFilter.DONE -> AppIcons.DownloadDone
                                DownloadStatusFilter.FAILED -> AppIcons.ErrorOutline
                            },
                            contentDescription = stringResource(filter.labelRes),
                            tint = if (filter == DownloadStatusFilter.FAILED && count > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(count.toString(), maxLines = 1)
                    }
                },
            )
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
    // Counts are precomputed off the main thread in the ViewModel (over the full, unfiltered
    // chapter set) so the header shows the complete tally even while a filter is active.
    val counts = novelDownloads.counts
    val stats = buildList {
        if (counts.downloaded > 0) add(pluralStringResource(R.plurals.downloads_count_downloaded, counts.downloaded, counts.downloaded))
        if (counts.queued > 0) add(pluralStringResource(R.plurals.downloads_count_queued, counts.queued, counts.queued))
        if (counts.downloading > 0) add(pluralStringResource(R.plurals.downloads_count_downloading, counts.downloading, counts.downloading))
        if (counts.error > 0) add(pluralStringResource(R.plurals.downloads_count_failed, counts.error, counts.error))
    }.joinToString(" • ")

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
        val collapseLabel = stringResource(if (collapsed) R.string.action_expand else R.string.action_collapse)
        PlainTooltipIconButton(onClick = onToggleCollapse, tooltip = collapseLabel) {
            Icon(
                if (collapsed) AppIcons.ExpandMore else AppIcons.ExpandLess,
                contentDescription = collapseLabel,
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
                    AppIcons.HourglassEmpty,
                    contentDescription = stringResource(R.string.status_queued),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.DOWNLOADING,
                ChapterDownloadStatus.REDOWNLOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                ChapterDownloadStatus.DOWNLOADED -> Icon(
                    AppIcons.DownloadDone,
                    contentDescription = stringResource(R.string.status_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.ERROR,
                ChapterDownloadStatus.REDOWNLOAD_ERROR -> Icon(
                    AppIcons.ErrorOutline,
                    contentDescription = stringResource(R.string.status_error),
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
                    ChapterDownloadStatus.REDOWNLOAD_QUEUED -> PlainTooltipIconButton(onClick = onCancel, tooltip = stringResource(R.string.action_cancel)) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    ChapterDownloadStatus.ERROR,
                    ChapterDownloadStatus.REDOWNLOAD_ERROR -> PlainTooltipIconButton(onClick = onRetry, tooltip = stringResource(R.string.action_retry)) {
                        Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                    ChapterDownloadStatus.DOWNLOADED -> {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = stringResource(R.string.downloads_delete_options)) {
                                Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.downloads_delete_options))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.downloads_redownload)) },
                                    onClick = {
                                        menuExpanded = false
                                        onRedownload()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.downloads_delete_download)) },
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
