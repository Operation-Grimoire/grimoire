package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.NovelQuickViewSheet
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelCard(
    novel: Novel,
    inLibrary: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = novel.thumbnailUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                )
                if (inLibrary) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "In library",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = novel.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelListItem(
    novel: Novel,
    inLibrary: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = novel.thumbnailUrl,
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            if (inLibrary) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "In library",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = novel.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    filters: List<Filter<*>>,
    loadState: FilterLoadState,
    showSearchField: Boolean,
    onLoad: () -> Unit,
    onApply: (List<Filter<*>>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Edited states live alongside the filter list so Cancel doesn't mutate the source.
    val edited = remember(filters) {
        mutableStateMapOf<Int, Any?>().apply {
            filters.forEachIndexed { i, f -> put(i, f.state) }
        }
    }
    var sheetQuery by remember(filters) { mutableStateOf("") }

    val canApply = loadState is FilterLoadState.Ready || loadState is FilterLoadState.Loaded
    val canReload = loadState is FilterLoadState.Loaded || loadState is FilterLoadState.Error

    Column(Modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (canReload) {
                IconButton(onClick = onLoad) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload filters")
                }
            }
            TextButton(
                enabled = canApply,
                onClick = {
                    @Suppress("UNCHECKED_CAST")
                    filters.forEachIndexed { i, f ->
                        (f as Filter<Any?>).state = edited[i]
                    }
                    onApply(filters, sheetQuery)
                },
            ) { Text("Apply") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
        HorizontalDivider()

        FilterLoadHeader(loadState, onLoad)

        if (loadState !is FilterLoadState.NeedsLoad && loadState !is FilterLoadState.Loading) {
            if (showSearchField) {
                OutlinedTextField(
                    value = sheetQuery,
                    onValueChange = { sheetQuery = it },
                    label = { Text("Search") },
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
                "Filter options need to be loaded from the source.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoad) { Text("Load filters") }
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
            Text("Loading filters…")
        }
        is FilterLoadState.Error -> Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Failed to load filters: ${state.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoad) { Text("Retry") }
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
                Filter.TriState.STATE_INCLUDE -> "include"
                Filter.TriState.STATE_EXCLUDE -> "exclude"
                else -> "any"
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
            val children = remember(filter) {
                (filter.state as? List<*>).orEmpty().filterIsInstance<Filter<*>>()
            }
            // Mirror the dispatch in FilterGroupPickerDialog: count tri-state
            // include/exclude AND checked binary boxes so the badge reflects
            // whichever shape this Group's children take.
            val selectedCount = (state as? List<*>).orEmpty()
                .filterIsInstance<Filter<*>>()
                .count { child ->
                    when (child) {
                        is Filter.TriState -> child.state != Filter.TriState.STATE_IGNORE
                        is Filter.CheckBox -> child.state
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
                    if (selectedCount > 0) "$selectedCount selected" else "Any",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showPicker) {
                FilterGroupPickerDialog(
                    title = filter.name,
                    children = children,
                    onChanged = { onStateChange(children.toList()) },
                    onDismiss = { showPicker = false },
                )
            }
        }
    }
}

/**
 * A searchable picker for the children of a [Filter.Group]. Each row's
 * affordance is chosen from the child's runtime type: [Filter.CheckBox]
 * renders as a binary toggle, [Filter.TriState] cycles include/exclude/any.
 * Treating every child as tri-state regardless of declared type used to
 * write `Int` into a CheckBox's `Boolean state` and crash at the source
 * with `ClassCastException`.
 */
@Composable
private fun FilterGroupPickerDialog(
    title: String,
    children: List<Filter<*>>,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    // Compose-observable mirror of each child's state, keyed by index. The
    // value type matches the child filter type (Boolean for CheckBox, Int for
    // TriState) — the per-row branch below reads and writes the right shape.
    val states = remember(children) {
        mutableStateMapOf<Int, Any?>().apply {
            children.forEachIndexed { i, c -> put(i, c.state) }
        }
    }
    val filtered = remember(search, children) {
        val q = search.trim()
        children.indices.filter {
            q.isEmpty() || children[it].name.contains(q, ignoreCase = true)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter ${children.size} options…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    lazyItems(filtered, key = { it }) { idx ->
                        val child = children[idx]
                        when (child) {
                            is Filter.CheckBox -> {
                                val checked = states[idx] as? Boolean ?: false
                                CheckBoxPickerRow(name = child.name, checked = checked) {
                                    val next = !checked
                                    states[idx] = next
                                    child.state = next
                                    onChanged()
                                }
                            }
                            is Filter.TriState -> {
                                val current = states[idx] as? Int
                                    ?: Filter.TriState.STATE_IGNORE
                                TriStatePickerRow(name = child.name, state = current) {
                                    val next = when (current) {
                                        Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                                        Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                                        else -> Filter.TriState.STATE_IGNORE
                                    }
                                    states[idx] = next
                                    child.state = next
                                    onChanged()
                                }
                            }
                            // Other Filter subtypes (Text, Select, …) inside a
                            // Group aren't meaningful — skip rather than guess.
                            else -> Unit
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun CheckBoxPickerRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TriStatePickerRow(name: String, state: Int, onCycle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCycle() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        Text(
            when (state) {
                Filter.TriState.STATE_INCLUDE -> "include"
                Filter.TriState.STATE_EXCLUDE -> "exclude"
                else -> "any"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when (state) {
                Filter.TriState.STATE_INCLUDE -> MaterialTheme.colorScheme.primary
                Filter.TriState.STATE_EXCLUDE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
