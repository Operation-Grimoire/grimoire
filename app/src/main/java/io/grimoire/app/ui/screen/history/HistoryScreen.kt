package io.grimoire.app.ui.screen.history

import io.grimoire.app.ui.icon.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import io.grimoire.app.data.local.entity.BrowsingHistoryEntity
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.ui.component.dayKey
import io.grimoire.app.ui.component.dayLabel
import io.grimoire.app.ui.component.timeLabel

private const val READING_TAB = 0
private const val BROWSING_TAB = 1

/** Groups already-sorted (newest-first) entries into day buckets, preserving order. */
private fun <T> bucketByDay(items: List<T>, timestamp: (T) -> Long): List<Pair<Long, List<T>>> =
    items.groupBy { dayKey(timestamp(it)) }.toList()

/**
 * Folds a day's reading entries (newest-first) into runs of the same novel: each maximal
 * stretch of adjacent rows sharing (sourcePackage, novelUrl) becomes one group. A novel read
 * straight through is one group; switching novels and returning starts a fresh one.
 */
private fun groupConsecutiveByNovel(
    entries: List<ReadingHistoryEntity>,
): List<List<ReadingHistoryEntity>> {
    val groups = mutableListOf<MutableList<ReadingHistoryEntity>>()
    for (entry in entries) {
        val last = groups.lastOrNull()
        if (last != null && last[0].sourcePackage == entry.sourcePackage && last[0].novelUrl == entry.novelUrl) {
            last.add(entry)
        } else {
            groups.add(mutableListOf(entry))
        }
    }
    return groups
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit,
    onOpenNovel: (pkg: String, novelUrl: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val reading by viewModel.readingEntries.collectAsState()
    val browsing by viewModel.browsingEntries.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val currentPage = pagerState.currentPage.coerceIn(0, BROWSING_TAB)

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val clearSelection: () -> Unit = { selectedIds = emptySet() }
    // Reading and browsing ids are distinct PK spaces; reset selection when the tab
    // changes so an id can't leak across the two lists.
    LaunchedEffect(currentPage) { clearSelection() }

    val readingDays = remember(reading) { bucketByDay(reading) { it.openedAt } }
    val browsingDays = remember(browsing) { bucketByDay(browsing) { it.openedAt } }

    val visibleIds = remember(currentPage, reading, browsing) {
        if (currentPage == READING_TAB) reading.map { it.id }.toSet()
        else browsing.map { it.id }.toSet()
    }

    val toggle: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    // Selecting a reading group's header toggles every chapter under it.
    val toggleAll: (List<Long>) -> Unit = { ids ->
        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids.toSet() else selectedIds + ids
    }
    // Reading groups start collapsed; this tracks the ones the user expanded (keyed by the
    // group's first entry id).
    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }

    BackHandler(enabled = selectionMode) { clearSelection() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        selectedIds = if (selectedIds.containsAll(visibleIds)) emptySet() else visibleIds
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                            Icon(AppIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("History") },
                    actions = {
                        val hasEntries = if (currentPage == READING_TAB) reading.isNotEmpty() else browsing.isNotEmpty()
                        if (hasEntries) {
                            Box {
                                PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = "More actions") {
                                    Icon(AppIcons.MoreVert, contentDescription = "More actions")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Clear ${if (currentPage == READING_TAB) "reading" else "browsing"} history") },
                                        onClick = {
                                            menuExpanded = false
                                            showClearConfirm = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            TooltipBottomBar(visible = selectionMode) {
                TooltipIconButton(
                    icon = AppIcons.DeleteHistory,
                    label = "Delete",
                    onClick = {
                        if (currentPage == READING_TAB) viewModel.deleteReading(selectedIds)
                        else viewModel.deleteBrowsing(selectedIds)
                        clearSelection()
                    },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) { padding ->
        SwipeTabRow(
            tabs = listOf("Reading", "Browsing"),
            modifier = Modifier.padding(padding),
            pagerState = pagerState,
            style = SwipeTabStyle.Primary,
        ) { page ->
            if (page == READING_TAB) {
                if (reading.isEmpty()) {
                    EmptyHistory("No reading history yet.\nChapters you open in the reader appear here.")
                } else {
                    LazyColumn {
                        readingDays.forEach { (dayKeyValue, dayEntries) ->
                            item(key = "r-day-$dayKeyValue") { DayHeader(dayEntries.first().openedAt) }
                            // Consecutive chapters of the same novel within a day collapse into
                            // one expandable group (newest-first), so a reading session reads as
                            // a single entry rather than a stack of near-identical rows.
                            groupConsecutiveByNovel(dayEntries).forEach { group ->
                                if (group.size == 1) {
                                    val entry = group[0]
                                    item(key = "r-${entry.id}") {
                                        HistoryRow(
                                            thumbnailUrl = entry.novelThumbnailUrl,
                                            title = entry.novelTitle,
                                            subtitle = entry.chapterName,
                                            openedAt = entry.openedAt,
                                            selected = entry.id in selectedIds,
                                            onClick = {
                                                if (selectionMode) toggle(entry.id)
                                                else onOpenReader(entry.sourcePackage, entry.novelUrl, entry.chapterUrl)
                                            },
                                            onLongClick = { toggle(entry.id) },
                                        )
                                    }
                                } else {
                                    val groupKey = group.first().id
                                    val collapsed = groupKey !in expandedGroups
                                    val ids = group.map { it.id }
                                    val toggleCollapse = {
                                        expandedGroups = if (collapsed) expandedGroups + groupKey
                                        else expandedGroups - groupKey
                                    }
                                    item(key = "r-group-$groupKey") {
                                        ReadingGroupHeader(
                                            group = group,
                                            collapsed = collapsed,
                                            selected = ids.all { it in selectedIds },
                                            onClick = {
                                                if (selectionMode) toggleAll(ids) else toggleCollapse()
                                            },
                                            onLongClick = {
                                                if (selectionMode) toggleCollapse() else toggleAll(ids)
                                            },
                                            onToggleCollapse = toggleCollapse,
                                        )
                                    }
                                    if (!collapsed) {
                                        items(group.size, key = { "r-ch-${group[it].id}" }) { i ->
                                            val entry = group[i]
                                            ChildRail {
                                                ReadingChildRow(
                                                    chapterName = entry.chapterName,
                                                    openedAt = entry.openedAt,
                                                    selected = entry.id in selectedIds,
                                                    onClick = {
                                                        if (selectionMode) toggle(entry.id)
                                                        else onOpenReader(entry.sourcePackage, entry.novelUrl, entry.chapterUrl)
                                                    },
                                                    onLongClick = { toggle(entry.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (browsing.isEmpty()) {
                    EmptyHistory("No browsing history yet.\nNovels you open in Browse (and haven't added to your library) appear here.")
                } else {
                    LazyColumn {
                        browsingDays.forEach { (dayKeyValue, entries) ->
                            item(key = "b-day-$dayKeyValue") { DayHeader(entries.first().openedAt) }
                            items(entries.size, key = { "b-${entries[it].id}" }) { i ->
                                val entry = entries[i]
                                HistoryRow(
                                    thumbnailUrl = entry.novelThumbnailUrl,
                                    title = entry.novelTitle,
                                    subtitle = null,
                                    openedAt = entry.openedAt,
                                    selected = entry.id in selectedIds,
                                    onClick = {
                                        if (selectionMode) toggle(entry.id)
                                        else onOpenNovel(entry.sourcePackage, entry.novelUrl)
                                    },
                                    onLongClick = { toggle(entry.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        val reading = currentPage == READING_TAB
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear ${if (reading) "reading" else "browsing"} history?") },
            text = {
                Text(
                    if (reading) "This permanently removes every entry from your reading history. Your library and chapters are not affected."
                    else "This permanently removes every entry from your browsing history. Your library is not affected."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    if (reading) viewModel.clearReading() else viewModel.clearBrowsing()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DayHeader(timestamp: Long) {
    Text(
        text = dayLabel(timestamp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    openedAt: Long,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Text(
                text = timeLabel(openedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/** Collapsible header for a same-novel run of reading entries (mirrors the Updates group row). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingGroupHeader(
    group: List<ReadingHistoryEntity>,
    collapsed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCollapse: () -> Unit,
) {
    val first = group.first()
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            AsyncImage(
                model = first.novelThumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
        headlineContent = {
            Text(
                text = first.novelTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = "${group.size} chapters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = timeLabel(first.openedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlainTooltipIconButton(
                    onClick = onToggleCollapse,
                    tooltip = if (collapsed) "Expand" else "Collapse",
                ) {
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

/** A chapter row nested under a [ReadingGroupHeader]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingChildRow(
    chapterName: String,
    openedAt: Long,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        } else {
            ListItemDefaults.colors()
        },
        headlineContent = {
            Text(
                text = chapterName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                text = timeLabel(openedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/**
 * Wraps a grouped chapter row with a vertical rail down its left edge (centered under the
 * header cover), so the nesting under the group header reads at a glance.
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
private fun EmptyHistory(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
