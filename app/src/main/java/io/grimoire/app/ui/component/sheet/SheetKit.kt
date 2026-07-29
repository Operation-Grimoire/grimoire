package io.grimoire.app.ui.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.ui.icon.*

/**
 * Trailing content for the summary rows: the current-selection text plus a
 * chevron, so the row reads as "tap to open a picker" rather than a heading.
 */
@Composable
private fun SummaryTrailing(text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            AppIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The shared idioms for bottom-sheet content. Every option/filter sheet uses
 * these instead of hand-rolling titles, section labels, and switch rows, so
 * the sheets read as one surface family.
 */

/** Sheet header title — one style (titleMedium) across every sheet. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Section label inside a sheet — labelMedium on onSurfaceVariant. */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

/**
 * Entry row for a large multi-select: title left, "All" / "N selected"
 * summary right (primary-tinted when active). Tap opens the associated
 * [SearchableMultiSelectDialog].
 */
@Composable
fun MultiSelectSummaryRow(
    title: String,
    selectedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            SummaryTrailing(
                text = if (selectedCount == 0) {
                    stringResource(R.string.filter_all)
                } else {
                    pluralStringResource(
                        R.plurals.source_filter_selected_count,
                        selectedCount,
                        selectedCount,
                    )
                },
                active = selectedCount > 0,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

/**
 * Entry row for a large include/exclude selection: "All" while nothing is
 * set, else "N included · M excluded". Tap opens the associated
 * [SearchableTriStateDialog].
 */
@Composable
fun TriStateSummaryRow(
    title: String,
    includedCount: Int,
    excludedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = includedCount > 0 || excludedCount > 0
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            SummaryTrailing(
                text = if (!active) {
                    stringResource(R.string.filter_all)
                } else {
                    stringResource(R.string.filter_included_excluded, includedCount, excludedCount)
                },
                active = active,
            )
        },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/**
 * Binary setting row: title, optional supporting hint, trailing switch. The
 * whole row toggles — never just the switch.
 */
@Composable
fun SheetSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = modifier.clickable { onCheckedChange(!checked) },
    )
}
