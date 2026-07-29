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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.grimoire.api.model.filter.Filter
import io.grimoire.app.R

/**
 * A searchable picker for the children of a [Filter.Group]. Each row's
 * affordance is chosen from the child's runtime type: [Filter.CheckBox]
 * renders as a binary toggle, [Filter.TriState] cycles include/exclude/any.
 * Treating every child as tri-state regardless of declared type used to
 * write `Int` into a CheckBox's `Boolean state` and crash at the source
 * with `ClassCastException`.
 *
 * [children] supplies names and types only and is never written — edits flow
 * through [states]/[onStatesChange] (index-aligned with [children]) so the
 * filter sheet's Cancel genuinely discards them. The live `Filter.state` is
 * only touched by the sheet's Apply.
 */
@Composable
internal fun FilterGroupPickerDialog(
    title: String,
    children: List<Filter<*>>,
    states: List<Any?>,
    onStatesChange: (List<Any?>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, children) {
        val q = search.trim()
        children.indices.filter {
            q.isEmpty() || children[it].name.contains(q, ignoreCase = true)
        }
    }

    fun stateAt(idx: Int): Any? = states.getOrNull(idx)
    fun changeAt(idx: Int, value: Any?) =
        onStatesChange(children.indices.map { if (it == idx) value else stateAt(it) })

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
                    placeholder = {
                        Text(pluralStringResource(R.plurals.source_filter_options_placeholder, children.size, children.size))
                    },
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
                    items(filtered, key = { it }) { idx ->
                        when (val child = children[idx]) {
                            is Filter.CheckBox -> {
                                val checked = stateAt(idx) as? Boolean ?: false
                                CheckBoxPickerRow(name = child.name, checked = checked) {
                                    changeAt(idx, !checked)
                                }
                            }
                            is Filter.TriState -> {
                                val current = stateAt(idx) as? Int
                                    ?: Filter.TriState.STATE_IGNORE
                                TriStatePickerRow(name = child.name, state = current) {
                                    changeAt(
                                        idx,
                                        when (current) {
                                            Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                                            Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                                            else -> Filter.TriState.STATE_IGNORE
                                        },
                                    )
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
                val anySelected = children.indices.any { i ->
                    when (children[i]) {
                        is Filter.TriState -> (stateAt(i) as? Int ?: Filter.TriState.STATE_IGNORE) != Filter.TriState.STATE_IGNORE
                        is Filter.CheckBox -> stateAt(i) as? Boolean ?: false
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
                            onStatesChange(
                                children.map { child ->
                                    when (child) {
                                        is Filter.CheckBox -> false
                                        is Filter.TriState -> Filter.TriState.STATE_IGNORE
                                        else -> child.state
                                    }
                                },
                            )
                        },
                    ) { Text(stringResource(R.string.action_clear)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
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
                Filter.TriState.STATE_INCLUDE -> stringResource(R.string.source_filter_include)
                Filter.TriState.STATE_EXCLUDE -> stringResource(R.string.source_filter_exclude)
                else -> stringResource(R.string.source_filter_any)
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
