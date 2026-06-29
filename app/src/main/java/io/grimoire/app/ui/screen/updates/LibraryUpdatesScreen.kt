package io.grimoire.app.ui.screen.updates

import io.grimoire.app.ui.icon.*
import androidx.activity.compose.BackHandler
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import io.grimoire.app.ui.component.ChapterItem
import io.grimoire.app.ui.component.ChapterStatusTrailing
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.ui.theme.premiumGold
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** New chapters from one novel found in a single library refresh, identified by [key]. */
private data class UpdateGroup(
    val key: Pair<Long, Long>,
    val entries: List<LibraryUpdateEntity>,
) {
    val first: LibraryUpdateEntity get() = entries.first()
}

private const val SUBSCRIBED_TAB = 0
private const val ALL_TAB = 1

/**
 * Buckets log entries into day-grouped novel update groups. Each library refresh
 * inserts a novel's new chapters with the same timestamp, so (novelId, foundAt)
 * identifies one novel's findings from one sync; those groups are then grouped by
 * day. Returns day buckets in the entries' existing order (newest first).
 */
private fun bucketDays(entries: List<LibraryUpdateEntity>): List<Pair<Long, List<UpdateGroup>>> =
    entries
        .groupBy { it.novelId to it.foundAt }
        .map { (key, l) -> UpdateGroup(key, l.sortedBy { it.chapterNumber }) }
        .groupBy { dayKey(it.first.foundAt) }
        .toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryUpdatesScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit,
    onOpenNovel: (pkg: String, novelUrl: String) -> Unit,
    viewModel: LibraryUpdatesViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val chaptersByEntryId by viewModel.chaptersByEntryId.collectAsState()
    val subscribedNovelIds by viewModel.subscribedNovelIds.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // Groups start expanded; this tracks the ones the user has collapsed, so a
    // fresh log shows every novel's chapters open by default.
    var collapsedGroups by remember { mutableStateOf(setOf<Pair<Long, Long>>()) }

    // When the user has subscribed any novel to notifications, split the log into
    // a "Subscribed" and an "All" tab. Each tab is its own day-grouped timeline,
    // so the day headers (Today / Yesterday / …) never bleed across the two
    // groupings the way stacked sections did. With nothing subscribed there is
    // only one timeline and no tab row.
    val hasSubscribed = subscribedNovelIds.isNotEmpty()
    val allDays = remember(entries) { bucketDays(entries) }
    val subscribedDays = remember(entries, subscribedNovelIds) {
        bucketDays(entries.filter { it.novelId in subscribedNovelIds })
    }
    // 0 = Subscribed, 1 = All. With nothing subscribed there is a single page
    // (and no tab row); the pager drives both tab selection and swipe.
    val pagerState = rememberPagerState(pageCount = { if (hasSubscribed) 2 else 1 })
    val currentPage = pagerState.currentPage.coerceIn(0, if (hasSubscribed) ALL_TAB else 0)
    fun daysForPage(page: Int) =
        if (hasSubscribed && page == SUBSCRIBED_TAB) subscribedDays else allDays
    // If the user un-subscribes everything while the "All" page is showing, the
    // page count drops to 1 — settle back onto the only remaining page.
    LaunchedEffect(hasSubscribed) {
        if (!hasSubscribed && pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }
    val visibleEntryIds = remember(currentPage, subscribedDays, allDays) {
        daysForPage(currentPage)
            .flatMap { (_, groups) -> groups.flatMap { g -> g.entries.map { it.id } } }
            .toSet()
    }

    var selectedEntryIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedEntryIds.isNotEmpty()
    val clearSelection: () -> Unit = { selectedEntryIds = emptySet() }
    val toggleEntry: (Long) -> Unit = { id ->
        selectedEntryIds = if (id in selectedEntryIds) selectedEntryIds - id else selectedEntryIds + id
    }
    // Selecting a novel header (long-press, or tap while in selection mode)
    // toggles every entry under it: if all are already selected, deselect them;
    // otherwise add the ones that aren't. The header's "selected" state is then
    // derived from "all its entries are selected", so unselecting any single
    // chapter automatically un-highlights the header.
    val toggleNovelEntries: (List<Long>) -> Unit = { entryIds ->
        selectedEntryIds = if (entryIds.all { it in selectedEntryIds }) {
            selectedEntryIds - entryIds.toSet()
        } else {
            selectedEntryIds + entryIds
        }
    }

    BackHandler(enabled = selectionMode) { clearSelection() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedEntryIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        selectedEntryIds = if (selectedEntryIds.containsAll(visibleEntryIds)) {
                            emptySet()
                        } else {
                            visibleEntryIds
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                            Icon(AppIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Updates") },
                    actions = {
                        Box {
                            PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = "More actions") {
                                Icon(AppIcons.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear log") },
                                    onClick = {
                                        menuExpanded = false
                                        showClearConfirm = true
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            TooltipBottomBar(visible = selectionMode) {
                val selectedChapters = selectedEntryIds.mapNotNull { chaptersByEntryId[it] }
                val showMarkRead = selectedChapters.any { !it.read }
                val showMarkUnread = selectedChapters.any { it.read }
                val showDownload = selectedChapters.any {
                    !it.locked &&
                        (it.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
                            it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal)
                }
                val showCancel = selectedChapters.any {
                    it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS
                }
                val showDeleteDownload = selectedChapters.any {
                    it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal
                }
                val novelIdsInSelection = selectedEntryIds
                    .mapNotNull { id -> entries.firstOrNull { it.id == id }?.novelId }
                    .distinct()
                val openNovelSample = if (novelIdsInSelection.size == 1) {
                    entries.firstOrNull { it.novelId == novelIdsInSelection.first() }
                } else null
                TooltipIconButton(
                    visible = openNovelSample != null,
                    icon = AppIcons.OpenInNew,
                    label = "Open novel",
                    onClick = {
                        openNovelSample?.let {
                            onOpenNovel(it.sourcePackage, it.novelUrl)
                            clearSelection()
                        }
                    },
                )
                TooltipIconButton(
                    visible = showMarkRead,
                    icon = AppIcons.DoneAll,
                    label = "Mark read",
                    onClick = {
                        viewModel.setEntriesRead(selectedEntryIds, true)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = showMarkUnread,
                    icon = AppIcons.RemoveDone,
                    label = "Mark unread",
                    onClick = {
                        viewModel.setEntriesRead(selectedEntryIds, false)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = showDownload,
                    icon = AppIcons.Download,
                    label = "Download",
                    onClick = {
                        viewModel.downloadEntries(selectedEntryIds)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = showCancel,
                    icon = AppIcons.Close,
                    label = "Cancel",
                    onClick = {
                        viewModel.cancelDownloadEntries(selectedEntryIds)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    visible = showDeleteDownload,
                    icon = AppIcons.FileDownloadOff,
                    label = "Delete download",
                    onClick = {
                        viewModel.deleteDownloadEntries(selectedEntryIds)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = AppIcons.DeleteHistory,
                    label = "Delete from log",
                    onClick = {
                        viewModel.deleteEntries(selectedEntryIds)
                        clearSelection()
                    },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No updates yet.\nNew chapters found by a library refresh appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SwipeTabRow(
                tabs = if (hasSubscribed) listOf("Subscribed", "All") else listOf("Updates"),
                modifier = Modifier.padding(padding),
                pagerState = pagerState,
                style = SwipeTabStyle.Primary,
                hideTabRowForSingleTab = true,
            ) { page ->
                val days = daysForPage(page)
                if (days.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No updates from subscribed novels yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn {
                        // Each refresh inserts a novel's new chapters with the same
                        // timestamp, so (novelId, foundAt) groups one novel's findings
                        // from one sync; those groups are then bucketed by day.
                        days.forEach { (dayKeyValue, dayGroups) ->
                            item(key = "day-$dayKeyValue") {
                                Text(
                                    text = dayLabel(dayGroups.first().first.foundAt),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            dayGroups.forEach { group ->
                                if (group.entries.size == 1) {
                                    val entry = group.first
                                    val liveChapter = chaptersByEntryId[entry.id]
                                    item(key = "single-${entry.id}") {
                                        UpdateRow(
                                            entry = entry,
                                            chapter = liveChapter,
                                            selected = entry.id in selectedEntryIds,
                                            selectionMode = selectionMode,
                                            onClick = {
                                                if (selectionMode) toggleEntry(entry.id)
                                                else if (liveChapter?.locked == true) {
                                                    onOpenNovel(entry.sourcePackage, entry.novelUrl)
                                                } else {
                                                    onOpenReader(entry.sourcePackage, entry.novelUrl, entry.chapterUrl)
                                                }
                                            },
                                            onLongClick = { toggleEntry(entry.id) },
                                            onDownload = { liveChapter?.let(viewModel::downloadChapter) },
                                            onCancelDownload = { liveChapter?.let(viewModel::cancelDownload) },
                                            onDeleteDownload = { liveChapter?.let(viewModel::deleteDownload) },
                                            onRedownload = { liveChapter?.let(viewModel::redownloadChapter) },
                                        )
                                    }
                                } else {
                                    val collapsed = group.key in collapsedGroups
                                    item(key = "group-${group.first.id}") {
                                        val toggleCollapse = {
                                            collapsedGroups = if (collapsed) {
                                                collapsedGroups - group.key
                                            } else {
                                                collapsedGroups + group.key
                                            }
                                        }
                                        val groupEntryIds = group.entries.map { it.id }
                                        UpdateGroupHeader(
                                            group = group,
                                            chaptersByEntryId = chaptersByEntryId,
                                            collapsed = collapsed,
                                            selected = groupEntryIds.all { it in selectedEntryIds },
                                            onClick = {
                                                if (selectionMode) toggleNovelEntries(groupEntryIds)
                                                else toggleCollapse()
                                            },
                                            onLongClick = {
                                                if (selectionMode) toggleCollapse()
                                                else toggleNovelEntries(groupEntryIds)
                                            },
                                            onToggleCollapse = toggleCollapse,
                                        )
                                    }
                                    if (!collapsed) {
                                        items(
                                            count = group.entries.size,
                                            key = { "chapter-${group.entries[it].id}" },
                                        ) { index ->
                                            val entry = group.entries[index]
                                            val chapter = chaptersByEntryId[entry.id]
                                                ?: stubChapterFromEntry(entry)
                                            ChildRail {
                                                ChapterItem(
                                                    chapter = chapter,
                                                    selected = entry.id in selectedEntryIds,
                                                    selectionMode = selectionMode,
                                                    onClick = {
                                                        onOpenReader(
                                                            entry.sourcePackage,
                                                            entry.novelUrl,
                                                            entry.chapterUrl,
                                                        )
                                                    },
                                                    onLockedClick = {
                                                        onOpenNovel(entry.sourcePackage, entry.novelUrl)
                                                    },
                                                    onToggleSelection = { toggleEntry(entry.id) },
                                                    onDownload = { chaptersByEntryId[entry.id]?.let(viewModel::downloadChapter) },
                                                    onCancelDownload = { chaptersByEntryId[entry.id]?.let(viewModel::cancelDownload) },
                                                    onDeleteDownload = { chaptersByEntryId[entry.id]?.let(viewModel::deleteDownload) },
                                                    onRedownload = { chaptersByEntryId[entry.id]?.let(viewModel::redownloadChapter) },
                                                )
                                            }
                                        }
                                        item(key = "group-end-${group.first.id}") {
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear updates log?") },
            text = { Text("This permanently removes every entry from the updates log. Your library and chapters are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearLog()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Fallback ChapterEntity built from the log snapshot when the real row has been
 * replaced by a later refresh. Keeps the locked badge visible at least.
 */
private fun stubChapterFromEntry(entry: LibraryUpdateEntity): ChapterEntity = ChapterEntity(
    id = entry.id,
    novelId = entry.novelId,
    url = entry.chapterUrl,
    name = entry.chapterName,
    chapterNumber = entry.chapterNumber,
    locked = entry.locked,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRow(
    entry: LibraryUpdateEntity,
    chapter: ChapterEntity?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onRedownload: () -> Unit,
) {
    ListItem(
        colors = updateListItemColors(selected),
        leadingContent = { NovelCover(entry.novelThumbnailUrl) },
        headlineContent = {
            Text(
                text = entry.novelTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (entry.unlockedFromLocked) UnlockedTag()
                Text(
                    text = entry.chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = timeLabel(entry.foundAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChapterStatusTrailing(
                    chapter = chapter ?: stubChapterFromEntry(entry),
                    selectionMode = selectionMode,
                    onLockedClick = onClick,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteDownload = onDeleteDownload,
                    onRedownload = onRedownload,
                )
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateGroupHeader(
    group: UpdateGroup,
    chaptersByEntryId: Map<Long, ChapterEntity>,
    collapsed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCollapse: () -> Unit,
) {
    val total = group.entries.size
    val lockedCount = group.entries.count { e ->
        chaptersByEntryId[e.id]?.locked ?: e.locked
    }
    val downloadedCount = group.entries.count { e ->
        chaptersByEntryId[e.id]?.downloadStatus in ChapterDownloadStatus.HAS_CONTENT_ORDINALS
    }
    val unlockedCount = group.entries.count { it.unlockedFromLocked }
    // "All downloaded" treats locked entries as unavailable, since they can't be
    // downloaded without unlocking on the source.
    val downloadableTotal = total - lockedCount
    val allDownloaded = downloadableTotal > 0 && downloadedCount >= downloadableTotal

    ListItem(
        colors = updateListItemColors(selected),
        leadingContent = { NovelCover(group.first.novelThumbnailUrl) },
        headlineContent = {
            Text(
                text = group.first.novelTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val parts = buildList {
                    add("$total new")
                    if (lockedCount > 0) add("$lockedCount locked")
                    if (downloadedCount > 0) add("$downloadedCount downloaded")
                }
                Text(
                    text = parts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (allDownloaded) {
                    Icon(
                        imageVector = AppIcons.CheckCircle,
                        contentDescription = "All downloaded",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (unlockedCount > 0) {
                    UnlockedTag(label = "$unlockedCount unlocked")
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = timeLabel(group.first.foundAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlainTooltipIconButton(onClick = onToggleCollapse, tooltip = if (collapsed) "Expand" else "Collapse") {
                    Icon(
                        imageVector = if (collapsed) AppIcons.ExpandMore else AppIcons.ExpandLess,
                        contentDescription = if (collapsed) "Expand" else "Collapse",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/**
 * Shared [ListItem] colors for an updates row. A selected row gets a translucent
 * primary tint; everything else uses the default surface. Going through
 * [ListItem] (instead of a hand-laid Row) keeps every updates row at the
 * standard two-line list height so a row can never stretch to fill extra space.
 */
@Composable
private fun updateListItemColors(selected: Boolean) =
    if (selected) {
        ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        )
    } else {
        ListItemDefaults.colors()
    }

@Composable
private fun UnlockedTag(label: String = "Unlocked") {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.premiumGold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.premiumGold.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Wraps a chapter row belonging to an expanded novel group, drawing a vertical
 * rail down its left edge so the nesting under the group header reads at a
 * glance. The rail sits under the header's cover; the chapter content fills the
 * rest of the row.
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
                    // Center the rail under the header cover: 16dp ListItem pad + 20dp (half of the 40dp cover).
                    val x = 36.dp.toPx()
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

@Composable
private fun NovelCover(thumbnailUrl: String?) {
    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        modifier = Modifier
            .size(width = 40.dp, height = 56.dp)
            .clip(RoundedCornerShape(4.dp)),
    )
}

private fun dayKey(timestamp: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun dayLabel(timestamp: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val day = dayKey(timestamp)
    return when (day) {
        today -> "Today"
        today - 24 * 60 * 60 * 1000L -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

private fun timeLabel(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
