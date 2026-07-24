package io.grimoire.app.ui.screen.novelupdates

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.grimoire.app.ui.component.AppSearchField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuBrowseSort
import io.grimoire.app.data.novelupdates.NuGenres
import io.grimoire.app.data.novelupdates.NuLanguages
import io.grimoire.app.data.novelupdates.NuNovelType
import io.grimoire.app.data.novelupdates.NuStoryStatus
import io.grimoire.app.data.novelupdates.NuTag
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheetState) {
            AdvancedFilterSheet(
                current = filter,
                tags = tags,
                tagsLoading = tagsLoading,
                onApply = {
                    showFilters = false
                    viewModel.applyFilter(it)
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilterSheet(
    current: NuBrowseFilter,
    tags: List<NuTag>,
    tagsLoading: Boolean,
    onApply: (NuBrowseFilter) -> Unit,
) {
    fun namesOf(ids: List<String>, table: Map<String, String>) =
        ids.mapNotNull { id -> table.entries.firstOrNull { it.value == id }?.key }

    var sort by remember { mutableStateOf(current.sort) }
    var ascending by remember { mutableStateOf(current.orderAscending) }
    var status by remember { mutableStateOf(current.storyStatus) }
    var genresMatchAll by remember { mutableStateOf(current.genresMatchAll) }
    var tagsMatchAll by remember { mutableStateOf(current.tagsMatchAll) }
    val genreInc = remember { namesOf(current.genresInclude, NuGenres.all).toMutableStateList() }
    val genreExc = remember { namesOf(current.genresExclude, NuGenres.all).toMutableStateList() }
    val langs = remember { namesOf(current.languages, NuLanguages.all).toMutableStateList() }
    val types = remember {
        current.novelTypes.mapNotNull { id -> NuNovelType.entries.firstOrNull { it.id == id } }
            .toMutableStateList()
    }
    val tagInc = remember { current.tagsInclude.toMutableStateList() }
    val tagExc = remember { current.tagsExclude.toMutableStateList() }

    var tagPicker by remember { mutableStateOf<TagPickerTarget?>(null) }

    fun toggle(list: MutableList<String>, v: String) { if (!list.remove(v)) list.add(v) }
    fun <T> toggleT(list: MutableList<T>, v: T) { if (!list.remove(v)) list.add(v) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.nu_order_by), style = MaterialTheme.typography.titleSmall)
        ChipFlow {
            NuBrowseSort.entries.forEach { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { sort = s },
                    label = {
                        Text(
                            stringResource(
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
                            ),
                        )
                    },
                )
            }
        }
        FilterChip(
            selected = ascending,
            onClick = { ascending = !ascending },
            label = { Text(stringResource(if (ascending) R.string.nu_ascending else R.string.nu_descending)) },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nu_genres), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilterChip(
                selected = genresMatchAll,
                onClick = { genresMatchAll = !genresMatchAll },
                label = { Text(stringResource(if (genresMatchAll) R.string.nu_match_all else R.string.nu_match_any)) },
            )
        }
        ChipFlow {
            NuGenres.all.keys.forEach { name ->
                FilterChip(
                    selected = name in genreInc,
                    onClick = { toggle(genreInc, name) },
                    label = { Text(localizedNuGenre(name)) },
                )
            }
        }
        Text(stringResource(R.string.nu_exclude_genres), style = MaterialTheme.typography.titleSmall)
        ChipFlow {
            NuGenres.all.keys.forEach { name ->
                FilterChip(
                    selected = name in genreExc,
                    onClick = { toggle(genreExc, name) },
                    label = { Text(localizedNuGenre(name)) },
                )
            }
        }

        Text(stringResource(R.string.nu_language), style = MaterialTheme.typography.titleSmall)
        ChipFlow {
            NuLanguages.all.keys.forEach { name ->
                FilterChip(
                    selected = name in langs,
                    onClick = { toggle(langs, name) },
                    label = { Text(localizedNuLanguage(name)) },
                )
            }
        }

        Text(stringResource(R.string.nu_novel_type), style = MaterialTheme.typography.titleSmall)
        ChipFlow {
            NuNovelType.entries.forEach { t ->
                FilterChip(
                    selected = t in types,
                    onClick = { toggleT(types, t) },
                    label = {
                        Text(
                            stringResource(
                                when (t) {
                                    NuNovelType.LIGHT_NOVEL -> R.string.nu_type_light_novel
                                    NuNovelType.PUBLISHED_NOVEL -> R.string.nu_type_published_novel
                                    NuNovelType.WEB_NOVEL -> R.string.nu_type_web_novel
                                },
                            ),
                        )
                    },
                )
            }
        }

        Text(stringResource(R.string.nu_story_status), style = MaterialTheme.typography.titleSmall)
        ChipFlow {
            NuStoryStatus.entries.forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = {
                        Text(
                            stringResource(
                                when (s) {
                                    NuStoryStatus.ANY -> R.string.source_filter_any
                                    NuStoryStatus.COMPLETED -> R.string.library_status_completed
                                    NuStoryStatus.ONGOING -> R.string.library_status_ongoing
                                    NuStoryStatus.HIATUS -> R.string.library_status_hiatus
                                },
                            ),
                        )
                    },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nu_tags), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilterChip(
                selected = tagsMatchAll,
                onClick = { tagsMatchAll = !tagsMatchAll },
                label = { Text(stringResource(if (tagsMatchAll) R.string.nu_match_all else R.string.nu_match_any)) },
            )
        }
        OutlinedButton(
            onClick = { tagPicker = TagPickerTarget.INCLUDE },
            enabled = !tagsLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    tagsLoading -> stringResource(R.string.nu_loading_tags)
                    tagInc.isEmpty() -> stringResource(R.string.nu_include_tags)
                    else -> stringResource(R.string.nu_include_tags_count, tagInc.size)
                },
            )
        }
        OutlinedButton(
            onClick = { tagPicker = TagPickerTarget.EXCLUDE },
            enabled = !tagsLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (tagExc.isEmpty()) stringResource(R.string.nu_exclude_tags)
                else stringResource(R.string.nu_exclude_tags_count, tagExc.size),
            )
        }

        Button(
            onClick = {
                onApply(
                    NuBrowseFilter(
                        query = current.query,
                        sort = sort,
                        orderAscending = ascending,
                        languages = langs.mapNotNull { NuLanguages.all[it] },
                        genresInclude = genreInc.mapNotNull { NuGenres.all[it] },
                        genresExclude = genreExc.mapNotNull { NuGenres.all[it] },
                        genresMatchAll = genresMatchAll,
                        novelTypes = types.map { it.id },
                        storyStatus = status,
                        tagsInclude = tagInc.toList(),
                        tagsExclude = tagExc.toList(),
                        tagsMatchAll = tagsMatchAll,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_apply)) }
    }

    tagPicker?.let { target ->
        val selected = if (target == TagPickerTarget.INCLUDE) tagInc else tagExc
        TagPickerDialog(
            title = stringResource(
                if (target == TagPickerTarget.INCLUDE) R.string.nu_include_tags
                else R.string.nu_exclude_tags,
            ),
            tags = tags,
            selectedIds = selected,
            onToggle = { id -> toggle(selected, id) },
            onDismiss = { tagPicker = null },
        )
    }
}

private enum class TagPickerTarget { INCLUDE, EXCLUDE }

@Composable
private fun TagPickerDialog(
    title: String,
    tags: List<NuTag>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, tags) {
        if (search.isBlank()) tags
        else tags.filter { it.name.contains(search, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = {
                        Text(pluralStringResource(R.plurals.nu_filter_tags_placeholder, tags.size, tags.size))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { tag ->
                        val checked = tag.id in selectedIds
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            trailingContent = {
                                if (checked) {
                                    Icon(
                                        AppIcons.Check,
                                        contentDescription = stringResource(R.string.content_description_selected),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(tag.id) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}
