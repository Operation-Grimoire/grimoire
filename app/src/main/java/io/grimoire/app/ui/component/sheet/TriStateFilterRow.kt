package io.grimoire.app.ui.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R

/** Include/exclude filter state for a [TriStateFilterRow]. */
enum class FilterTriState {
    ANY, INCLUDE, EXCLUDE;

    fun next(): FilterTriState = when (this) {
        ANY -> INCLUDE
        INCLUDE -> EXCLUDE
        EXCLUDE -> ANY
    }
}

/**
 * Full-width filter row: name left, cycling state label right — Any (neutral)
 * → Include (primary) → Exclude (error). Tap anywhere to cycle. One list of
 * these replaces separate include/exclude sections.
 */
@Composable
fun TriStateFilterRow(
    name: String,
    state: FilterTriState,
    onStateChange: (FilterTriState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onStateChange(state.next()) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        Text(
            when (state) {
                FilterTriState.INCLUDE -> stringResource(R.string.source_filter_include)
                FilterTriState.EXCLUDE -> stringResource(R.string.source_filter_exclude)
                FilterTriState.ANY -> stringResource(R.string.source_filter_any)
            },
            style = MaterialTheme.typography.labelMedium,
            color = when (state) {
                FilterTriState.INCLUDE -> MaterialTheme.colorScheme.primary
                FilterTriState.EXCLUDE -> MaterialTheme.colorScheme.error
                FilterTriState.ANY -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
