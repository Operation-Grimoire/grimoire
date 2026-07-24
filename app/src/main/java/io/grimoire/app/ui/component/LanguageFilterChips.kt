package io.grimoire.app.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.util.languageLabel

/**
 * Horizontally scrolling row of single-select language filter chips shared by the
 * Browse home and Extensions screens. [languages] are raw lang codes; a leading
 * "All" chip clears the filter (null). Renders nothing when there's only one
 * language — a single-language list needs no filter.
 */
@Composable
internal fun LanguageFilterChips(
    languages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (languages.size < 2) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        languages.forEach { lang ->
            FilterChip(
                selected = selected == lang,
                onClick = { onSelect(lang) },
                label = { Text(languageLabel(lang)) },
            )
        }
    }
}

/**
 * Multi-select language chips for the persistent "which languages show in this
 * list" setting. [enabled] is the chosen set; an **empty set means "all"**, so
 * every chip (and the leading "All") reads as selected. Tapping "All" clears the
 * selection back to all; tapping a language toggles just that one.
 */
@Composable
internal fun LanguageMultiSelectChips(
    languages: List<String>,
    enabled: Set<String>,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (languages.size < 2) return
    val showAll = enabled.isEmpty()
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = showAll,
            onClick = onAll,
            label = { Text(stringResource(R.string.filter_all)) },
        )
        languages.forEach { lang ->
            FilterChip(
                selected = showAll || lang in enabled,
                onClick = { onToggle(lang) },
                label = { Text(languageLabel(lang)) },
            )
        }
    }
}
