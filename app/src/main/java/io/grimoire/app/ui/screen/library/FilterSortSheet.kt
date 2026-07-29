package io.grimoire.app.ui.screen.library

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import kotlin.math.roundToInt
import io.grimoire.app.data.preferences.NovelTypeFilter
import io.grimoire.app.data.preferences.SortDirection
import io.grimoire.app.data.preferences.SortField
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle

internal val STATUS_OPTIONS = listOf(
    1 to R.string.library_status_ongoing,
    2 to R.string.library_status_completed,
    3 to R.string.library_status_hiatus,
    4 to R.string.library_status_cancelled,
    0 to R.string.library_status_unknown,
)

internal val NOVEL_TYPE_OPTIONS = listOf(
    NovelTypeFilter.ALL to R.string.library_all,
    NovelTypeFilter.EPUB to R.string.library_type_epub,
    NovelTypeFilter.WEB to R.string.library_type_web,
)

internal val SORT_FIELD_OPTIONS = listOf(
    SortField.LAST_READ to R.string.library_sort_last_read,
    SortField.TITLE to R.string.library_sort_title,
    SortField.LAST_UPDATED to R.string.library_sort_last_updated,
    SortField.UNREAD to R.string.library_sort_unread_chapters,
    SortField.TOTAL to R.string.library_sort_total_chapters,
    SortField.USER_RATING to R.string.library_sort_your_rating,
)

@Composable
internal fun FilterSortContent(
    sortField: SortField,
    sortDirection: SortDirection,
    filterStatuses: Set<Int>,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    filterNotifyEnabled: Boolean,
    filterAutoDownloadEnabled: Boolean,
    filterMinUserRating: Int,
    filterMaxUserRating: Int,
    filterType: NovelTypeFilter,
    filterSourceIds: Set<Long>,
    librarySources: List<Pair<Long, String>>,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleFilterStatus: (Int?) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    onNotifyEnabledChange: (Boolean) -> Unit,
    onAutoDownloadEnabledChange: (Boolean) -> Unit,
    onUserRatingRangeChange: (Int, Int) -> Unit,
    onFilterTypeChange: (NovelTypeFilter) -> Unit,
    onToggleFilterSource: (Long?) -> Unit,
) {
    SwipeTabRow(
        tabs = listOf(
            stringResource(R.string.library_filter_tab),
            stringResource(R.string.library_sort_tab),
        ),
        style = SwipeTabStyle.Secondary,
        // Inside a sheet: wrap the page height instead of filling the screen.
        fillHeight = false,
    ) { page ->
        // Each page scrolls on its own so a long status/source list (or the
        // sort list on a short screen) is always reachable inside the sheet.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            when (page) {
                0 -> FilterTab(
                    filterStatuses = filterStatuses,
                    filterUnreadOnly = filterUnreadOnly,
                    filterDownloadedOnly = filterDownloadedOnly,
                    filterNotifyEnabled = filterNotifyEnabled,
                    filterAutoDownloadEnabled = filterAutoDownloadEnabled,
                    filterMinUserRating = filterMinUserRating,
                    filterMaxUserRating = filterMaxUserRating,
                    filterType = filterType,
                    filterSourceIds = filterSourceIds,
                    librarySources = librarySources,
                    onToggleFilterStatus = onToggleFilterStatus,
                    onUnreadOnlyChange = onUnreadOnlyChange,
                    onDownloadedOnlyChange = onDownloadedOnlyChange,
                    onNotifyEnabledChange = onNotifyEnabledChange,
                    onAutoDownloadEnabledChange = onAutoDownloadEnabledChange,
                    onUserRatingRangeChange = onUserRatingRangeChange,
                    onFilterTypeChange = onFilterTypeChange,
                    onToggleFilterSource = onToggleFilterSource,
                )
                1 -> SortTab(
                    sortField = sortField,
                    sortDirection = sortDirection,
                    onSortFieldChange = onSortFieldChange,
                    onToggleSortDirection = onToggleSortDirection,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTab(
    filterStatuses: Set<Int>,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    filterNotifyEnabled: Boolean,
    filterAutoDownloadEnabled: Boolean,
    filterMinUserRating: Int,
    filterMaxUserRating: Int,
    filterType: NovelTypeFilter,
    filterSourceIds: Set<Long>,
    librarySources: List<Pair<Long, String>>,
    onToggleFilterStatus: (Int?) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    onNotifyEnabledChange: (Boolean) -> Unit,
    onAutoDownloadEnabledChange: (Boolean) -> Unit,
    onUserRatingRangeChange: (Int, Int) -> Unit,
    onFilterTypeChange: (NovelTypeFilter) -> Unit,
    onToggleFilterSource: (Long?) -> Unit,
) {
    Column {
        var showStatusPicker by remember { mutableStateOf(false) }
        val statusLabels = STATUS_OPTIONS.associate { (ordinal, labelRes) ->
            ordinal.toString() to stringResource(labelRes)
        }
        io.grimoire.app.ui.component.sheet.MultiSelectSummaryRow(
            title = stringResource(R.string.library_filter_status),
            selectedCount = filterStatuses.size,
            onClick = { showStatusPicker = true },
        )
        if (showStatusPicker) {
            io.grimoire.app.ui.component.sheet.SearchableMultiSelectDialog(
                title = stringResource(R.string.library_filter_status),
                options = STATUS_OPTIONS.map { it.first.toString() },
                optionLabel = { statusLabels[it].orEmpty() },
                isChecked = { it.toInt() in filterStatuses },
                onToggle = { onToggleFilterStatus(it.toInt()) },
                onClear = { onToggleFilterStatus(null) },
                clearEnabled = filterStatuses.isNotEmpty(),
                onDismiss = { showStatusPicker = false },
            )
        }
        if (librarySources.size > 1) {
            var showSourcePicker by remember { mutableStateOf(false) }
            val sourceLabels = librarySources.associate { (id, label) -> id.toString() to label }
            io.grimoire.app.ui.component.sheet.MultiSelectSummaryRow(
                title = stringResource(R.string.library_filter_source),
                selectedCount = filterSourceIds.size,
                onClick = { showSourcePicker = true },
            )
            if (showSourcePicker) {
                io.grimoire.app.ui.component.sheet.SearchableMultiSelectDialog(
                    title = stringResource(R.string.library_filter_source),
                    options = librarySources.map { it.first.toString() },
                    optionLabel = { sourceLabels[it].orEmpty() },
                    isChecked = { it.toLong() in filterSourceIds },
                    onToggle = { onToggleFilterSource(it.toLong()) },
                    onClear = { onToggleFilterSource(null) },
                    clearEnabled = filterSourceIds.isNotEmpty(),
                    onDismiss = { showSourcePicker = false },
                )
            }
        }
        io.grimoire.app.ui.component.sheet.SheetSectionLabel(stringResource(R.string.library_filter_type))
        io.grimoire.app.ui.component.sheet.SingleChoiceSegmented(
            options = NOVEL_TYPE_OPTIONS.map { it.first },
            selected = filterType,
            onSelect = onFilterTypeChange,
            label = { option ->
                stringResource(NOVEL_TYPE_OPTIONS.first { it.first == option }.second)
            },
            modifier = Modifier.padding(vertical = 4.dp),
        )
        // Local drag state so the pref is written once per gesture (on release), not on
        // every frame. Re-seeded whenever the persisted values change.
        var range by remember(filterMinUserRating, filterMaxUserRating) {
            mutableStateOf(filterMinUserRating.toFloat()..filterMaxUserRating.toFloat())
        }
        val lo = range.start.roundToInt()
        val hi = range.endInclusive.roundToInt()
        val ratingActive = lo > 1 || hi < 10
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.library_filter_rating_range),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (ratingActive) "$lo – $hi" else stringResource(R.string.library_filter_rating_any),
                style = MaterialTheme.typography.labelMedium,
                color = if (ratingActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            onValueChangeFinished = {
                onUserRatingRangeChange(
                    range.start.roundToInt(),
                    range.endInclusive.roundToInt(),
                )
            },
            valueRange = 1f..10f,
            steps = 8, // 10 discrete stops
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.library_filter_unread_only)) },
            supportingContent = {
                Text(stringResource(R.string.library_filter_unread_only_description))
            },
            trailingContent = {
                Switch(checked = filterUnreadOnly, onCheckedChange = onUnreadOnlyChange)
            },
            modifier = Modifier.clickable { onUnreadOnlyChange(!filterUnreadOnly) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.library_filter_has_downloads)) },
            supportingContent = {
                Text(stringResource(R.string.library_filter_has_downloads_description))
            },
            trailingContent = {
                Switch(checked = filterDownloadedOnly, onCheckedChange = onDownloadedOnlyChange)
            },
            modifier = Modifier.clickable { onDownloadedOnlyChange(!filterDownloadedOnly) },
        )
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.library_filter_notifications_on))
            },
            supportingContent = {
                Text(stringResource(R.string.library_filter_notifications_on_description))
            },
            trailingContent = {
                Switch(checked = filterNotifyEnabled, onCheckedChange = onNotifyEnabledChange)
            },
            modifier = Modifier.clickable { onNotifyEnabledChange(!filterNotifyEnabled) },
        )
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.library_filter_auto_download_on))
            },
            supportingContent = {
                Text(stringResource(R.string.library_filter_auto_download_on_description))
            },
            trailingContent = {
                Switch(checked = filterAutoDownloadEnabled, onCheckedChange = onAutoDownloadEnabledChange)
            },
            modifier = Modifier.clickable { onAutoDownloadEnabledChange(!filterAutoDownloadEnabled) },
        )
    }
}

@Composable
private fun SortTab(
    sortField: SortField,
    sortDirection: SortDirection,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortDirection: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SORT_FIELD_OPTIONS.forEach { (field, labelRes) ->
            val selected = sortField == field
            // Tapping the active row flips the direction in place; tapping any other
            // row promotes that field to active without changing the direction. This
            // is the standard Material sort pattern and avoids needing a separate
            // arrow button per row.
            ListItem(
                headlineContent = { Text(stringResource(labelRes)) },
                leadingContent = {
                    if (selected) {
                        Icon(
                            imageVector = if (sortDirection == SortDirection.ASC) {
                                AppIcons.ArrowUpward
                            } else AppIcons.ArrowDownward,
                            contentDescription = if (sortDirection == SortDirection.ASC) {
                                stringResource(R.string.library_sort_ascending_description)
                            } else {
                                stringResource(R.string.library_sort_descending_description)
                            },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        // Reserve the leading slot so labels stay aligned across rows.
                        Spacer(Modifier.size(24.dp))
                    }
                },
                modifier = Modifier.clickable {
                    if (selected) onToggleSortDirection() else onSortFieldChange(field)
                },
            )
        }
    }
}
