package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.NovelQuickViewSheet
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.model.Filter
import io.grimoire.api.model.Novel
import io.grimoire.app.data.preferences.BrowseDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBrowseScreen(
    onNavigateBack: () -> Unit,
    onNovelClick: (Novel) -> Unit = {},
    onChapterClick: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit = { _, _, _ -> },
    onOpenWebView: (url: String) -> Unit = {},
    onOpenSourceSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SourceBrowseViewModel = hiltViewModel(),
) {
    val novels by viewModel.novels.collectAsState()
    val libraryUrls by viewModel.libraryUrls.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val query by viewModel.query.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var quickView by remember { mutableStateOf<Novel?>(null) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    val gridState = rememberLazyGridState()
    val reachedBottom by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= gridState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedBottom) {
        snapshotFlow { reachedBottom }.collect { if (it) viewModel.loadMore() }
    }
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    val filters by viewModel.filters.collectAsState()
    val filterLoadState by viewModel.filterLoadState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            viewModel.setQuery("")
                            if (mode == BrowseMode.SEARCH) viewModel.setMode(BrowseMode.POPULAR)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (searchActive) "Close search" else "Back",
                        )
                    }
                },
                title = {
                    if (searchActive) {
                        AppSearchField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            placeholder = "Search ${viewModel.sourceName}…",
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            onSearch = { viewModel.submitSearch() },
                        )
                    } else {
                        Text(viewModel.sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { onOpenWebView(viewModel.sourceBaseUrl) }) {
                            Icon(Icons.Default.Language, contentDescription = "Open in WebView")
                        }
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (viewModel.isConfigurable) {
                            IconButton(onClick = onOpenSourceSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Source settings")
                            }
                        }
                        IconButton(onClick = {
                            viewModel.setDisplayMode(
                                if (displayMode == BrowseDisplayMode.GRID) BrowseDisplayMode.LIST
                                else BrowseDisplayMode.GRID
                            )
                        }) {
                            Icon(
                                if (displayMode == BrowseDisplayMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle display mode",
                            )
                        }
                    }
                    if (filterLoadState != FilterLoadState.None) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!searchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = mode == BrowseMode.POPULAR,
                        onClick = { viewModel.setMode(BrowseMode.POPULAR) },
                        label = { Text("Popular") },
                    )
                    FilterChip(
                        selected = mode == BrowseMode.LATEST,
                        onClick = { viewModel.setMode(BrowseMode.LATEST) },
                        label = { Text("Latest") },
                    )
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && novels.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = if (displayMode == BrowseDisplayMode.GRID) GridCells.Fixed(gridColumns) else GridCells.Fixed(1),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(novels, key = { it.url }) { novel ->
                        if (displayMode == BrowseDisplayMode.GRID) {
                            NovelCard(
                                novel = novel,
                                inLibrary = novel.url in libraryUrls,
                                onClick = { onNovelClick(novel) },
                                onLongClick = { quickView = novel },
                            )
                        } else {
                            NovelListItem(
                                novel = novel,
                                inLibrary = novel.url in libraryUrls,
                                onClick = { onNovelClick(novel) },
                                onLongClick = { quickView = novel },
                            )
                        }
                    }
                    if (isLoadingMore || (hasMore && novels.isNotEmpty())) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    quickView?.let { novel ->
        NovelQuickViewSheet(
            packageName = viewModel.packageName,
            novelUrl = novel.url,
            onOpenDetails = { onNovelClick(novel) },
            onChapterClick = { chapterUrl ->
                onChapterClick(viewModel.packageName, novel.url, chapterUrl)
            },
            onDismiss = { quickView = null },
        )
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = filterSheetState,
        ) {
            FilterSheet(
                filters = filters,
                loadState = filterLoadState,
                showSearchField = viewModel.supportsSearchWithFilters,
                onLoad = { viewModel.loadFilterOptions() },
                onApply = { applied, sheetQuery ->
                    viewModel.applyFilters(applied, sheetQuery)
                    showFilters = false
                },
                onDismiss = { showFilters = false },
            )
        }
    }
}

