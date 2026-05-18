package io.grimoire.app.ui.screen.novelupdates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuBrowseSort
import io.grimoire.app.data.novelupdates.NuGenres
import io.grimoire.app.data.novelupdates.NuLanguages
import io.grimoire.app.data.novelupdates.NuListingFilter
import io.grimoire.app.data.novelupdates.NuRankingType
import io.grimoire.app.data.novelupdates.NuSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUpdatesBrowserScreen(
    onNavigateBack: () -> Unit,
    onSeriesClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovelUpdatesBrowserViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val rankingType by viewModel.rankingType.collectAsState()
    val query by viewModel.query.collectAsState()
    val listingFilter by viewModel.listingFilter.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()

    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    var showFilters by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val reachedBottom by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { reachedBottom }.collect { if (it) viewModel.loadMore() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("NovelUpdates") },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip("Rankings", mode == NuBrowseMode.RANKINGS) {
                    viewModel.setMode(NuBrowseMode.RANKINGS)
                }
                ModeChip("Latest", mode == NuBrowseMode.LATEST) {
                    viewModel.setMode(NuBrowseMode.LATEST)
                }
                ModeChip("Search", mode == NuBrowseMode.SEARCH) {
                    viewModel.setMode(NuBrowseMode.SEARCH)
                }
            }

            if (mode == NuBrowseMode.RANKINGS) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NuRankingType.entries.forEach { type ->
                        ModeChip(type.label, rankingType == type) {
                            viewModel.setRankingType(type)
                        }
                    }
                }
            }

            if (mode == NuBrowseMode.SEARCH) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search NovelUpdates…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        viewModel.submitSearch()
                    }),
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null && results.isEmpty() -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
                    }
                    results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "No results",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.url }) { item ->
                            NuCoverCard(item) { onSeriesClick(item.slug) }
                        }
                        if (isLoadingMore || (hasMore && results.isNotEmpty())) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = filterSheetState,
        ) {
            NuFilterSheet(
                showSearchFilters = mode == NuBrowseMode.SEARCH,
                listingFilter = listingFilter,
                searchFilter = searchFilter,
                onApply = { listing, search ->
                    showFilters = false
                    if (mode == NuBrowseMode.SEARCH) {
                        viewModel.applySearchFilter(search)
                    } else {
                        viewModel.applyListingFilter(listing)
                    }
                },
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun NuCoverCard(item: NuSearchResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp)),
        )
        Text(
            item.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.rating?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    " %.1f".format(r),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val SORT_LABELS = listOf(
    NuBrowseSort.POPULAR to "Popular",
    NuBrowseSort.LATEST to "Recently added",
    NuBrowseSort.LAST_UPDATED to "Last updated",
    NuBrowseSort.RATING to "Rating",
    NuBrowseSort.RANK to "Rank",
    NuBrowseSort.TITLE to "Title (A–Z)",
)

private val GENRE_ID_TO_NAME = NuGenres.all.entries.associate { (k, v) -> v to k }
private val LANG_ID_TO_NAME = NuLanguages.all.entries.associate { (k, v) -> v to k }

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun NuFilterSheet(
    showSearchFilters: Boolean,
    listingFilter: NuListingFilter,
    searchFilter: NuBrowseFilter,
    onApply: (NuListingFilter, NuBrowseFilter) -> Unit,
) {
    // Selections held by display name; mapped back to NU ids on Apply.
    val genreInclude = remember {
        (if (showSearchFilters) searchFilter.genresInclude else listingFilter.genres)
            .mapNotNull { GENRE_ID_TO_NAME[it] }.toMutableStateList()
    }
    val genreExclude = remember {
        searchFilter.genresExclude.mapNotNull { GENRE_ID_TO_NAME[it] }.toMutableStateList()
    }
    val languages = remember {
        (if (showSearchFilters) searchFilter.languages else listingFilter.languages)
            .mapNotNull { LANG_ID_TO_NAME[it] }.toMutableStateList()
    }
    var matchAll by remember {
        mutableStateOf(
            if (showSearchFilters) searchFilter.genresMatchAll else listingFilter.genresMatchAll,
        )
    }
    var sort by remember { mutableStateOf(searchFilter.sort) }

    fun toggle(list: MutableList<String>, value: String) {
        if (!list.remove(value)) list.add(value)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSearchFilters) {
            Text("Order by", style = MaterialTheme.typography.titleSmall)
            ChipFlow {
                SORT_LABELS.forEach { (value, label) ->
                    FilterChip(
                        selected = sort == value,
                        onClick = { sort = value },
                        label = { Text(label) },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Genres",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (matchAll) "Match: ALL" else "Match: ANY",
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(onClick = { matchAll = !matchAll }) {
                Text(if (matchAll) "AND" else "OR")
            }
        }
        ChipFlow {
            NuGenres.all.keys.forEach { name ->
                FilterChip(
                    selected = name in genreInclude,
                    onClick = { toggle(genreInclude, name) },
                    label = { Text(name) },
                )
            }
        }

        if (showSearchFilters) {
            Text("Exclude genres", style = MaterialTheme.typography.titleSmall)
            ChipFlow {
                NuGenres.all.keys.forEach { name ->
                    FilterChip(
                        selected = name in genreExclude,
                        onClick = { toggle(genreExclude, name) },
                        label = { Text(name) },
                    )
                }
            }
        }

        Text("Language", style = MaterialTheme.typography.titleSmall)
        ChipFlow {
            NuLanguages.all.keys.forEach { name ->
                FilterChip(
                    selected = name in languages,
                    onClick = { toggle(languages, name) },
                    label = { Text(name) },
                )
            }
        }

        Button(
            onClick = {
                val incIds = genreInclude.mapNotNull { NuGenres.all[it] }
                val excIds = genreExclude.mapNotNull { NuGenres.all[it] }
                val langIds = languages.mapNotNull { NuLanguages.all[it] }
                onApply(
                    NuListingFilter(
                        languages = langIds,
                        genres = incIds,
                        genresMatchAll = matchAll,
                    ),
                    NuBrowseFilter(
                        sort = sort,
                        languages = langIds,
                        genresInclude = incIds,
                        genresExclude = excIds,
                        genresMatchAll = matchAll,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply") }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}
