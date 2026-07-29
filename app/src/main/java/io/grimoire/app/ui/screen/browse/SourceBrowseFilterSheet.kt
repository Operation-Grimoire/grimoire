package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.grimoire.api.model.filter.Filter
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    filters: List<Filter<*>>,
    loadState: FilterLoadState,
    showSearchField: Boolean,
    initialQuery: String,
    onLoad: () -> Unit,
    onApply: (List<Filter<*>>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Edited states live alongside the filter list so Cancel doesn't mutate the
    // source. A Group's entry is a *copy* of its children's states (the children
    // themselves are shared with the VM), so group edits stay cancellable too.
    val edited = remember(filters) {
        mutableStateMapOf<Int, Any?>().apply {
            filters.forEachIndexed { i, f ->
                put(
                    i,
                    if (f is Filter.Group<*>) f.childFilters().map { it.state } else f.state,
                )
            }
        }
    }
    // Seed from the active query so applying filters doesn't wipe a search the
    // user already ran (only shown for sources that search with filters).
    var sheetQuery by remember(filters) { mutableStateOf(initialQuery) }

    val canApply = loadState is FilterLoadState.Ready || loadState is FilterLoadState.Loaded
    val canReload = loadState is FilterLoadState.Loaded || loadState is FilterLoadState.Error

    Column(Modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.source_browse_filters), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (canReload) {
                PlainTooltipIconButton(onClick = onLoad, tooltip = stringResource(R.string.source_filters_reload)) {
                    Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.source_filters_reload))
                }
            }
            TextButton(
                enabled = canApply,
                onClick = {
                    @Suppress("UNCHECKED_CAST")
                    filters.forEachIndexed { i, f ->
                        if (f is Filter.Group<*>) {
                            val states = edited[i] as? List<*> ?: return@forEachIndexed
                            f.childFilters().forEachIndexed { j, child ->
                                if (j < states.size) (child as Filter<Any?>).state = states[j]
                            }
                        } else {
                            (f as Filter<Any?>).state = edited[i]
                        }
                    }
                    onApply(filters, sheetQuery)
                },
            ) { Text(stringResource(R.string.action_apply)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
        HorizontalDivider()

        // Header above stays pinned; the filter list scrolls so long filter sets
        // (e.g. Royal Road's tags + advanced fields) stay reachable inside the sheet.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            FilterLoadHeader(loadState, onLoad)

            if (loadState !is FilterLoadState.NeedsLoad && loadState !is FilterLoadState.Loading) {
                if (showSearchField) {
                    OutlinedTextField(
                        value = sheetQuery,
                        onValueChange = { sheetQuery = it },
                        label = { Text(stringResource(R.string.action_search)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                filters.forEachIndexed { i, filter ->
                    FilterItem(
                        filter = filter,
                        state = edited[i],
                        onStateChange = { edited[i] = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterLoadHeader(state: FilterLoadState, onLoad: () -> Unit) {
    when (state) {
        FilterLoadState.NeedsLoad -> Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.source_filters_need_load),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoad) { Text(stringResource(R.string.source_filters_load)) }
        }
        FilterLoadState.Loading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.source_filters_loading))
        }
        is FilterLoadState.Error -> Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.source_filters_load_failed, state.message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoad) { Text(stringResource(R.string.action_retry)) }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterItem(
    filter: Filter<*>,
    state: Any?,
    onStateChange: (Any?) -> Unit,
) {
    when (filter) {
        is Filter.Header -> Text(
            filter.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        is Filter.Separator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
        is Filter.Text -> {
            val current = state as? String ?: ""
            OutlinedTextField(
                value = current,
                onValueChange = onStateChange,
                label = { Text(filter.name) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        is Filter.CheckBox -> {
            val checked = state as? Boolean ?: false
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStateChange(!checked) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { onStateChange(it) })
                Spacer(Modifier.width(8.dp))
                Text(filter.name)
            }
        }
        is Filter.TriState -> {
            val current = state as? Int ?: Filter.TriState.STATE_IGNORE
            val next = when (current) {
                Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                else -> Filter.TriState.STATE_IGNORE
            }
            val label = when (current) {
                Filter.TriState.STATE_INCLUDE -> stringResource(R.string.source_filter_include)
                Filter.TriState.STATE_EXCLUDE -> stringResource(R.string.source_filter_exclude)
                else -> stringResource(R.string.source_filter_any)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStateChange(next) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(filter.name, modifier = Modifier.weight(1f))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (current) {
                        Filter.TriState.STATE_INCLUDE -> MaterialTheme.colorScheme.primary
                        Filter.TriState.STATE_EXCLUDE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        is Filter.Select<*> -> {
            val selected = (state as? Int ?: 0).coerceIn(0, (filter.values.size - 1).coerceAtLeast(0))
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = filter.values.getOrNull(selected)?.toString().orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(filter.name) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    filter.values.forEachIndexed { i, value ->
                        DropdownMenuItem(
                            text = { Text(value.toString()) },
                            onClick = {
                                onStateChange(i)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        is Filter.Sort -> {
            val current = state as? Filter.Sort.Selection
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(filter.name, style = MaterialTheme.typography.bodyMedium)
                filter.values.forEachIndexed { i, value ->
                    val isSelected = current?.index == i
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStateChange(
                                    if (isSelected) Filter.Sort.Selection(i, !current!!.ascending)
                                    else Filter.Sort.Selection(i, false)
                                )
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onStateChange(Filter.Sort.Selection(i, false)) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(value, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Text(
                                if (current!!.ascending) "↑" else "↓",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        is Filter.Group<*> -> {
            // Children stay identity-stable for the screen's lifetime; only
            // their `state` mutates, so derive the list once.
            val children = remember(filter) { filter.childFilters() }
            // `state` here is the sheet's edited copy of the children's states,
            // index-aligned with [children] — never the live child objects.
            val stateList = (state as? List<*>).orEmpty()
            // Mirror the dispatch in FilterGroupPickerDialog: count tri-state
            // include/exclude AND checked binary boxes so the badge reflects
            // whichever shape this Group's children take.
            val selectedCount = children.indices.count { j ->
                when (children[j]) {
                    is Filter.TriState ->
                        (stateList.getOrNull(j) as? Int ?: Filter.TriState.STATE_IGNORE) !=
                            Filter.TriState.STATE_IGNORE
                    is Filter.CheckBox -> stateList.getOrNull(j) as? Boolean ?: false
                    else -> false
                }
            }
            var showPicker by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(filter.name, modifier = Modifier.weight(1f))
                Text(
                    if (selectedCount > 0) {
                        pluralStringResource(R.plurals.source_filter_selected_count, selectedCount, selectedCount)
                    } else {
                        stringResource(R.string.source_filter_any)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    AppIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showPicker) {
                FilterGroupPickerDialog(
                    title = filter.name,
                    children = children,
                    states = children.indices.map { stateList.getOrNull(it) ?: children[it].state },
                    onStatesChange = onStateChange,
                    onDismiss = { showPicker = false },
                )
            }
        }
    }
}

/** The live child filters of a [Filter.Group] (shared with the VM — read-only here). */
private fun Filter.Group<*>.childFilters(): List<Filter<*>> =
    (state as? List<*>).orEmpty().filterIsInstance<Filter<*>>()
