package io.grimoire.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Delete
import io.grimoire.ui.icon.DeleteSweep
import io.grimoire.ui.icon.History
import io.grimoire.ui.icon.PlayArrow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryPane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier = Modifier) {
    var confirmClear by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val scroll = rememberLazyListState()
    val coversByNovel = remember(state.library) {
        state.library.associate { it.key to it.novel.thumbnailUrl }
    }
    val groupedByDay = remember(state.history) {
        state.history.groupBy { Instant.ofEpochMilli(it.openedAtMillis).atZone(zone).toLocalDate() }.toList()
    }
    val today = LocalDate.now(zone)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reading history") },
                actions = {
                    PlainTooltipIconButton(
                        onClick = { confirmClear = true },
                        tooltip = "Clear history",
                        enabled = enabled && state.history.isNotEmpty(),
                    ) {
                        Icon(AppIcons.DeleteSweep, contentDescription = "Clear history")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
            ) {
                ListItem(
                    leadingContent = { Icon(AppIcons.History, contentDescription = null) },
                    headlineContent = { Text("Record opened chapters") },
                    supportingContent = { Text("Keep a local list of chapters you open.") },
                    trailingContent = {
                        Switch(checked = state.historyRecording, onCheckedChange = null, enabled = enabled)
                    },
                    modifier = Modifier.toggleable(
                        value = state.historyRecording,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = model::setHistoryRecording,
                    ),
                )
                HorizontalDivider()
                if (state.history.isEmpty()) {
                    DesktopEmptyState(
                        icon = AppIcons.History,
                        title = "No reading history yet.",
                        subtitle = if (state.historyRecording) {
                            "Chapters you open will appear here."
                        } else {
                            "Turn on recording above to keep track of chapters you open."
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        state = scroll,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        groupedByDay.forEach { (day, entries) ->
                            val dayHeader = when (day) {
                                today -> "Today"
                                today.minusDays(1) -> "Yesterday"
                                else -> dateFormat.format(day)
                            }
                            item(key = "day:$day") {
                                Text(
                                    dayHeader,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(entries, key = { it.id }) { entry ->
                                val coverUrl = coversByNovel[entry.chapter.key.novel]
                                val openedTime = timeFormat.format(Instant.ofEpochMilli(entry.openedAtMillis))
                                ListItem(
                                    leadingContent = {
                                        BookCover(
                                            url = coverUrl,
                                            title = entry.chapter.novelTitle,
                                            modifier = Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(6.dp)),
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            entry.chapter.novelTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                entry.chapter.chapterTitle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Text(
                                                openedTime,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            PlainTooltipIconButton(
                                                onClick = { model.readHistory(entry) },
                                                tooltip = "Open chapter",
                                                enabled = enabled,
                                            ) {
                                                Icon(AppIcons.PlayArrow, contentDescription = "Open chapter")
                                            }
                                            PlainTooltipIconButton(
                                                onClick = { model.removeHistory(entry.id) },
                                                tooltip = "Remove",
                                                enabled = enabled,
                                            ) {
                                                Icon(AppIcons.Delete, contentDescription = "Remove")
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable(enabled = enabled) { model.readHistory(entry) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.history.isNotEmpty()) {
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxHeight().align(Alignment.CenterEnd))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear reading history?") },
            text = { Text("This removes saved history entries from this library. You can still resume your reading.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; model.clearHistory() }, enabled = enabled) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
