package io.grimoire.app.ui.screen.updates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryUpdatesScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit,
    onOpenNovel: (pkg: String, novelUrl: String) -> Unit,
    viewModel: LibraryUpdatesViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(setOf<Pair<Long, Long>>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Updates") },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
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
            // Each refresh inserts a novel's new chapters with the same timestamp,
            // so (novelId, foundAt) groups one novel's findings from one sync.
            val days = remember(entries) {
                entries
                    .groupBy { it.novelId to it.foundAt }
                    // Newest chapter first so the order within a group is obvious.
                    .map { (key, list) -> UpdateGroup(key, list.sortedByDescending { it.chapterNumber }) }
                    .groupBy { dayKey(it.first.foundAt) }
            }
            LazyColumn(modifier = Modifier.padding(padding)) {
                days.forEach { (_, dayGroups) ->
                    item(key = "day-${dayKey(dayGroups.first().first.foundAt)}") {
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
                            item(key = "single-${entry.id}") {
                                UpdateRow(
                                    entry = entry,
                                    onClick = {
                                        onOpenReader(entry.sourcePackage, entry.novelUrl, entry.chapterUrl)
                                    },
                                    onLongClick = {
                                        onOpenNovel(entry.sourcePackage, entry.novelUrl)
                                    },
                                )
                            }
                        } else {
                            val collapsed = group.key !in expandedGroups
                            item(key = "group-${group.first.id}") {
                                UpdateGroupHeader(
                                    group = group,
                                    collapsed = collapsed,
                                    onToggle = {
                                        expandedGroups = if (collapsed) {
                                            expandedGroups + group.key
                                        } else {
                                            expandedGroups - group.key
                                        }
                                    },
                                    onLongClick = {
                                        onOpenNovel(group.first.sourcePackage, group.first.novelUrl)
                                    },
                                )
                            }
                            if (!collapsed) {
                                items(
                                    count = group.entries.size,
                                    key = { "chapter-${group.entries[it].id}" },
                                ) { index ->
                                    val entry = group.entries[index]
                                    ChapterUpdateRow(
                                        entry = entry,
                                        onClick = {
                                            onOpenReader(
                                                entry.sourcePackage,
                                                entry.novelUrl,
                                                entry.chapterUrl,
                                            )
                                        },
                                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRow(
    entry: LibraryUpdateEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NovelCover(entry.novelThumbnailUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.novelTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.chapterName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = timeLabel(entry.foundAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateGroupHeader(
    group: UpdateGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NovelCover(group.first.novelThumbnailUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.first.novelTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${group.entries.size} new chapters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = timeLabel(group.first.foundAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterUpdateRow(entry: LibraryUpdateEntity, onClick: () -> Unit) {
    val numberLabel = chapterNumberLabel(entry.chapterNumber)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = numberLabel?.let { label -> { Text(label) } },
        headlineContent = {
            Text(
                text = entry.chapterName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** "Chapter 142" / "Chapter 142.5", or null when the source gave no number. */
private fun chapterNumberLabel(chapterNumber: Float): String? {
    if (chapterNumber < 0f) return null
    val formatted = if (chapterNumber % 1f == 0f) {
        chapterNumber.toInt().toString()
    } else {
        chapterNumber.toString()
    }
    return "Chapter $formatted"
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
