package io.grimoire.app.ui.screen.novelupdates

import io.grimoire.app.ui.icon.*
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import io.grimoire.app.ui.component.AppSearchField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuBrowseSort
import io.grimoire.app.data.novelupdates.NuGenres
import io.grimoire.app.data.novelupdates.NuLanguages
import io.grimoire.app.data.novelupdates.NuNovelType
import io.grimoire.app.data.novelupdates.NuStoryStatus
import io.grimoire.app.data.novelupdates.NuTag
import io.grimoire.app.ui.component.dialog.FullScreenDialog
import io.grimoire.app.ui.component.sheet.FilterTriState
import io.grimoire.app.ui.component.sheet.MultiChoiceSegmented
import io.grimoire.app.ui.component.sheet.MultiSelectSummaryRow
import io.grimoire.app.ui.component.sheet.SearchableMultiSelectDialog
import io.grimoire.app.ui.component.sheet.SearchableTriStateDialog
import io.grimoire.app.ui.component.sheet.SheetSectionLabel
import io.grimoire.app.ui.component.sheet.SingleChoiceSegmented
import io.grimoire.app.ui.component.sheet.TriStateSummaryRow
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUpdatesSearchScreen(
    onNavigateBack: () -> Unit,
    onSeriesClick: (slug: String) -> Unit,
    onOpenWebView: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovelUpdatesSearchViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val tagsLoading by viewModel.tagsLoading.collectAsState()

    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    AppSearchField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.nu_search_label) + "…",
                        modifier = Modifier.fillMaxWidth(),
                        onSearch = { viewModel.submitSearch() },
                    )
                },
                actions = {
                    PlainTooltipIconButton(onClick = { onOpenWebView(viewModel.currentPageUrl()) }, tooltip = stringResource(R.string.action_open_in_webview)) {
                        Icon(AppIcons.Language, contentDescription = stringResource(R.string.action_open_in_webview))
                    }
                    PlainTooltipIconButton(onClick = { showFilters = true }, tooltip = stringResource(R.string.source_browse_filters)) {
                        Icon(AppIcons.FilterList, contentDescription = stringResource(R.string.source_browse_filters))
                    }
                },
            )
        },
    ) { padding ->
        NuResultsArea(
            results = results,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            error = error,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onSeriesClick = onSeriesClick,
            modifier = Modifier.padding(padding),
        )
    }

    if (showFilters) {
        AdvancedFilterDialog(
            current = filter,
            tags = tags,
            tagsLoading = tagsLoading,
            onDismiss = { showFilters = false },
            onApply = {
                showFilters = false
                viewModel.applyFilter(it)
            },
        )
    }
}

/**
 * Full-screen advanced-filter form. Edits stay local until the top-bar Apply
 * commits them; X/back is an explicit cancel. Genres and tags are single
 * include/exclude tri-state pickers instead of the old paired
 * include-list + exclude-list sections.
 */
@Composable
private fun AdvancedFilterDialog(
    current: NuBrowseFilter,
    tags: List<NuTag>,
    tagsLoading: Boolean,
    onDismiss: () -> Unit,
    onApply: (NuBrowseFilter) -> Unit,
) {
    fun namesOf(ids: List<String>, table: Map<String, String>) =
        ids.mapNotNull { id -> table.entries.firstOrNull { it.value == id }?.key }

    var sort by remember { mutableStateOf(current.sort) }
    var ascending by remember { mutableStateOf(current.orderAscending) }
    var status by remember { mutableStateOf(current.storyStatus) }
    var genresMatchAll by remember { mutableStateOf(current.genresMatchAll) }
    var tagsMatchAll by remember { mutableStateOf(current.tagsMatchAll) }
    val genreStates = remember {
        mutableStateMapOf<String, FilterTriState>().apply {
            namesOf(current.genresInclude, NuGenres.all).forEach { put(it, FilterTriState.INCLUDE) }
            namesOf(current.genresExclude, NuGenres.all).forEach { put(it, FilterTriState.EXCLUDE) }
        }
    }
    val langs = remember { namesOf(current.languages, NuLanguages.all).toMutableStateList() }
    val types = remember {
        current.novelTypes.mapNotNull { id -> NuNovelType.entries.firstOrNull { it.id == id } }
            .toMutableStateList()
    }
    val tagStates = remember {
        mutableStateMapOf<String, FilterTriState>().apply {
            current.tagsInclude.forEach { put(it, FilterTriState.INCLUDE) }
            current.tagsExclude.forEach { put(it, FilterTriState.EXCLUDE) }
        }
    }
    var showGenrePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    // Localized labels resolved once here — the picker dialogs take plain lambdas.
    val genreLabels = NuGenres.all.keys.associateWith { localizedNuGenre(it) }
    val languageLabels = NuLanguages.all.keys.associateWith { localizedNuLanguage(it) }
    val tagNames = remember(tags) { tags.associate { it.id to it.name } }

    fun includes(map: Map<String, FilterTriState>) = map.count { it.value == FilterTriState.INCLUDE }
    fun excludes(map: Map<String, FilterTriState>) = map.count { it.value == FilterTriState.EXCLUDE }

    FullScreenDialog(
        title = stringResource(R.string.source_browse_filters),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_apply),
        onConfirm = {
            onApply(
                NuBrowseFilter(
                    query = current.query,
                    sort = sort,
                    orderAscending = ascending,
                    languages = langs.mapNotNull { NuLanguages.all[it] },
                    genresInclude = genreStates.filterValues { it == FilterTriState.INCLUDE }
                        .keys.mapNotNull { NuGenres.all[it] },
                    genresExclude = genreStates.filterValues { it == FilterTriState.EXCLUDE }
                        .keys.mapNotNull { NuGenres.all[it] },
                    genresMatchAll = genresMatchAll,
                    novelTypes = types.map { it.id },
                    storyStatus = status,
                    tagsInclude = tagStates.filterValues { it == FilterTriState.INCLUDE }.keys.toList(),
                    tagsExclude = tagStates.filterValues { it == FilterTriState.EXCLUDE }.keys.toList(),
                    tagsMatchAll = tagsMatchAll,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SheetSectionLabel(stringResource(R.string.nu_order_by))
            SortDropdown(selected = sort, onSelect = { sort = it })
            SingleChoiceSegmented(
                options = listOf(false, true),
                selected = ascending,
                onSelect = { ascending = it },
                label = { asc ->
                    stringResource(if (asc) R.string.nu_ascending else R.string.nu_descending)
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )

            TriStateSummaryRow(
                title = stringResource(R.string.nu_genres),
                includedCount = includes(genreStates),
                excludedCount = excludes(genreStates),
                onClick = { showGenrePicker = true },
            )
            MatchModeSegmented(matchAll = genresMatchAll, onChange = { genresMatchAll = it })

            MultiSelectSummaryRow(
                title = stringResource(R.string.nu_language),
                selectedCount = langs.size,
                onClick = { showLanguagePicker = true },
            )

            SheetSectionLabel(stringResource(R.string.nu_novel_type))
            MultiChoiceSegmented(
                options = NuNovelType.entries.toList(),
                isChecked = { it in types },
                onToggle = { t -> if (!types.remove(t)) types.add(t) },
                label = { t ->
                    stringResource(
                        when (t) {
                            NuNovelType.LIGHT_NOVEL -> R.string.nu_type_light_novel
                            NuNovelType.PUBLISHED_NOVEL -> R.string.nu_type_published_novel
                            NuNovelType.WEB_NOVEL -> R.string.nu_type_web_novel
                        },
                    )
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )

            SheetSectionLabel(stringResource(R.string.nu_story_status))
            SingleChoiceSegmented(
                options = NuStoryStatus.entries.toList(),
                selected = status,
                onSelect = { status = it },
                label = { s ->
                    stringResource(
                        when (s) {
                            NuStoryStatus.ANY -> R.string.source_filter_any
                            NuStoryStatus.COMPLETED -> R.string.library_status_completed
                            NuStoryStatus.ONGOING -> R.string.library_status_ongoing
                            NuStoryStatus.HIATUS -> R.string.library_status_hiatus
                        },
                    )
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )

            TriStateSummaryRow(
                title = stringResource(
                    if (tagsLoading) R.string.nu_loading_tags else R.string.nu_tags,
                ),
                includedCount = includes(tagStates),
                excludedCount = excludes(tagStates),
                onClick = { showTagPicker = true },
                enabled = !tagsLoading,
            )
            MatchModeSegmented(matchAll = tagsMatchAll, onChange = { tagsMatchAll = it })
        }
    }

    if (showGenrePicker) {
        SearchableTriStateDialog(
            title = stringResource(R.string.nu_genres),
            options = NuGenres.all.keys.toList(),
            optionLabel = { genreLabels[it].orEmpty() },
            stateOf = { genreStates[it] ?: FilterTriState.ANY },
            onStateChange = { name, state -> genreStates[name] = state },
            onClear = { genreStates.clear() },
            clearEnabled = genreStates.values.any { it != FilterTriState.ANY },
            onDismiss = { showGenrePicker = false },
        )
    }
    if (showLanguagePicker) {
        SearchableMultiSelectDialog(
            title = stringResource(R.string.nu_language),
            options = NuLanguages.all.keys.toList(),
            optionLabel = { languageLabels[it].orEmpty() },
            isChecked = { it in langs },
            onToggle = { name -> if (!langs.remove(name)) langs.add(name) },
            onClear = { langs.clear() },
            clearEnabled = langs.isNotEmpty(),
            onDismiss = { showLanguagePicker = false },
        )
    }
    if (showTagPicker) {
        SearchableTriStateDialog(
            title = stringResource(R.string.nu_tags),
            options = tags.map { it.id },
            optionLabel = { tagNames[it].orEmpty() },
            stateOf = { tagStates[it] ?: FilterTriState.ANY },
            onStateChange = { id, state -> tagStates[id] = state },
            onClear = { tagStates.clear() },
            clearEnabled = tagStates.values.any { it != FilterTriState.ANY },
            onDismiss = { showTagPicker = false },
        )
    }
}

/** Any/All segmented toggle shown under a tri-state summary row. */
@Composable
internal fun MatchModeSegmented(matchAll: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmented(
        options = listOf(false, true),
        selected = matchAll,
        onSelect = onChange,
        label = { all ->
            stringResource(if (all) R.string.nu_match_all else R.string.nu_match_any)
        },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Exclusive sort choice — 8 options, so a dropdown instead of segments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(selected: NuBrowseSort, onSelect: (NuBrowseSort) -> Unit) {
    @Composable
    fun label(s: NuBrowseSort) = stringResource(
        when (s) {
            NuBrowseSort.READERS -> R.string.nu_sort_readers
            NuBrowseSort.LAST_UPDATED -> R.string.nu_sort_last_updated
            NuBrowseSort.RATING -> R.string.nu_sort_rating
            NuBrowseSort.RANK -> R.string.nu_sort_rank
            NuBrowseSort.REVIEWS -> R.string.nu_sort_reviews
            NuBrowseSort.CHAPTERS -> R.string.nu_sort_chapters
            NuBrowseSort.FREQUENCY -> R.string.nu_sort_frequency
            NuBrowseSort.TITLE -> R.string.nu_sort_title
        },
    )

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NuBrowseSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(label(s)) },
                    onClick = {
                        onSelect(s)
                        expanded = false
                    },
                )
            }
        }
    }
}
