package io.grimoire.app.ui.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.grimoire.app.R

/**
 * Searchable multi-select picker dialog — the scaling answer for option sets
 * too large for rows or segments (languages, tags, genres). Same shape as the
 * source-browse group picker: title, search field, lazy checkbox list,
 * Clear / Done footer. Selection state lives with the caller; every toggle is
 * applied immediately (Done just closes).
 */
@Composable
fun SearchableMultiSelectDialog(
    title: String,
    options: List<String>,
    optionLabel: (String) -> String,
    isChecked: (String) -> Boolean,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    clearEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, options) {
        val q = search.trim()
        options.filter { q.isEmpty() || optionLabel(it).contains(q, ignoreCase = true) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.source_filter_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(option) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isChecked(option), onCheckedChange = { onToggle(option) })
                            Spacer(Modifier.width(8.dp))
                            Text(optionLabel(option), modifier = Modifier.weight(1f))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(enabled = clearEnabled, onClick = onClear) {
                        Text(stringResource(R.string.action_clear))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}
