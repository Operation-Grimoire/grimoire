package io.grimoire.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HistoryPane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier = Modifier) {
    var confirmClear by remember { mutableStateOf(false) }
    val date = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Reading history", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }, enabled = enabled && state.history.isNotEmpty()) { Text("Clear history") }
        }
        Row(
            Modifier.toggleable(state.historyRecording, enabled = enabled, role = Role.Checkbox, onValueChange = model::setHistoryRecording),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.historyRecording, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Text("Record opened chapters")
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.history.isEmpty()) item { Text("No reading history yet.") }
            items(state.history, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.chapter.novelTitle, style = MaterialTheme.typography.titleMedium)
                        Text(entry.chapter.chapterTitle, style = MaterialTheme.typography.bodyMedium)
                        Text(date.format(Instant.ofEpochMilli(entry.openedAtMillis)), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { model.readHistory(entry) }, enabled = enabled) { Text("Open chapter") }
                    TextButton(onClick = { model.removeHistory(entry.id) }, enabled = enabled) { Text("Remove") }
                }
                HorizontalDivider()
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
