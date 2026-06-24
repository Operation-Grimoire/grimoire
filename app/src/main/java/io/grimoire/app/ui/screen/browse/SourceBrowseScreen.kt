package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.Arrangement
import io.grimoire.app.ui.component.PlainTooltipIconButton
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
    val cloudflareBlocked by viewModel.cloudflareBlocked.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val query by viewModel.query.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    // Start in active-search mode when opened with a preset query (deep link),
    // so the query bar is shown instead of the Popular tab.
    var searchActive by remember { mutableStateOf(viewModel.openedWithSearch) }
    var showFilters by remember { mutableStateOf(false) }
    var quickView by remember { mutableStateOf<Novel?>(null) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    // Held in the VM so scroll survives navigating to a novel and back; the VM
    // also resets it to the top on real mode changes (Popular / Latest / Search).
    val gridState = viewModel.gridState
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
        // Focus (and raise the keyboard) only when opening an empty search to
        // type. When we arrive with a preset query the results are already
        // shown, so don't pop the keyboard over them.
        if (searchActive && query.isBlank()) focusRequester.requestFocus()
    }

    val filters by viewModel.filters.collectAsState()
    val filterLoadState by viewModel.filterLoadState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            viewModel.setQuery("")
                            if (mode == BrowseMode.SEARCH) viewModel.setMode(BrowseMode.POPULAR)
                        } else {
                            onNavigateBack()
                        }
                    }, tooltip = if (searchActive) "Close search" else "Back") {
                        Icon(
                            if (searchActive) AppIcons.Close else AppIcons.ArrowBack,
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
                        PlainTooltipIconButton(onClick = { onOpenWebView(viewModel.sourceBaseUrl) }, tooltip = "Open in WebView") {
                            Icon(AppIcons.Language, contentDescription = "Open in WebView")
                        }
                        PlainTooltipIconButton(onClick = { searchActive = true }, tooltip = "Search") {
                            Icon(AppIcons.Search, contentDescription = "Search")
                        }
                        if (viewModel.isConfigurable) {
                            PlainTooltipIconButton(onClick = onOpenSourceSettings, tooltip = "Source settings") {
                                Icon(AppIcons.Settings, contentDescription = "Source settings")
                            }
                        }
                    }
                    if (filterLoadState != FilterLoadState.None) {
                        PlainTooltipIconButton(onClick = { showFilters = true }, tooltip = "Filters") {
                            Icon(AppIcons.FilterList, contentDescription = "Filters")
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
                cloudflareBlocked && novels.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            AppIcons.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Cloudflare challenge",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${viewModel.sourceName} is protected by a Cloudflare challenge " +
                                "that couldn't be solved automatically. Open it in WebView, " +
                                "solve the challenge there, then come back and retry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onOpenWebView(viewModel.sourceBaseUrl) }) {
                            Icon(AppIcons.Language, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open in WebView")
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
                    }
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

