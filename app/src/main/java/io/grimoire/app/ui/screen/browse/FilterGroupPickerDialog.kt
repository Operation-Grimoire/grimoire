package io.grimoire.app.ui.screen.browse

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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.grimoire.api.model.Filter

/**
 * A searchable picker for the children of a [Filter.Group]. Each row's
 * affordance is chosen from the child's runtime type: [Filter.CheckBox]
 * renders as a binary toggle, [Filter.TriState] cycles include/exclude/any.
 * Treating every child as tri-state regardless of declared type used to
 * write `Int` into a CheckBox's `Boolean state` and crash at the source
 * with `ClassCastException`.
 */
@Composable
internal fun FilterGroupPickerDialog(
    title: String,
    children: List<Filter<*>>,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    // Compose-observable mirror of each child's state, keyed by index. The
    // value type matches the child filter type (Boolean for CheckBox, Int for
    // TriState) — the per-row branch below reads and writes the right shape.
    val states = remember(children) {
        mutableStateMapOf<Int, Any?>().apply {
            children.forEachIndexed { i, c -> put(i, c.state) }
        }
    }
    val filtered = remember(search, children) {
        val q = search.trim()
        children.indices.filter {
            q.isEmpty() || children[it].name.contains(q, ignoreCase = true)
        }
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
                    placeholder = { Text("Filter ${children.size} options…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it }) { idx ->
                        val child = children[idx]
                        when (child) {
                            is Filter.CheckBox -> {
                                val checked = states[idx] as? Boolean ?: false
                                CheckBoxPickerRow(name = child.name, checked = checked) {
                                    val next = !checked
                                    states[idx] = next
                                    child.state = next
                                    onChanged()
                                }
                            }
                            is Filter.TriState -> {
                                val current = states[idx] as? Int
                                    ?: Filter.TriState.STATE_IGNORE
                                TriStatePickerRow(name = child.name, state = current) {
                                    val next = when (current) {
                                        Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                                        Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                                        else -> Filter.TriState.STATE_IGNORE
                                    }
                                    states[idx] = next
                                    child.state = next
                                    onChanged()
                                }
                            }
                            // Other Filter subtypes (Text, Select, …) inside a
                            // Group aren't meaningful — skip rather than guess.
                            else -> Unit
                        }
                    }
                }
                // Anything-selected check uses the same dispatch shape as the
                // rows above so Clear lights up for both CheckBox and TriState
                // children, and disabling it when nothing is set keeps the
                // affordance from looking actionable on an empty group.
                val anySelected = children.any { c ->
                    when (c) {
                        is Filter.TriState -> c.state != Filter.TriState.STATE_IGNORE
                        is Filter.CheckBox -> c.state
                        else -> false
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = anySelected,
                        onClick = {
                            children.forEachIndexed { i, child ->
                                when (child) {
                                    is Filter.CheckBox -> {
                                        child.state = false
                                        states[i] = false
                                    }
                                    is Filter.TriState -> {
                                        child.state = Filter.TriState.STATE_IGNORE
                                        states[i] = Filter.TriState.STATE_IGNORE
                                    }
                                    else -> Unit
                                }
                            }
                            onChanged()
                        },
                    ) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun CheckBoxPickerRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TriStatePickerRow(name: String, state: Int, onCycle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCycle() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        Text(
            when (state) {
                Filter.TriState.STATE_INCLUDE -> "include"
                Filter.TriState.STATE_EXCLUDE -> "exclude"
                else -> "any"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when (state) {
                Filter.TriState.STATE_INCLUDE -> MaterialTheme.colorScheme.primary
                Filter.TriState.STATE_EXCLUDE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
