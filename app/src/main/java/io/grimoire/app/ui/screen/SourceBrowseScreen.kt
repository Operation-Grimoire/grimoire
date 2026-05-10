package io.grimoire.app.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.api.model.Filter
import io.grimoire.api.model.Novel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBrowseScreen(
    onNavigateBack: () -> Unit,
    onNovelClick: (Novel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SourceBrowseViewModel = hiltViewModel(),
) {
    val novels by viewModel.novels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val query by viewModel.query.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

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

    val filters = viewModel.filters

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
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text("Search ${viewModel.sourceName}…") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                viewModel.submitSearch()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text(viewModel.sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    if (filters.isNotEmpty()) {
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
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(novels, key = { it.url }) { novel ->
                        NovelCard(novel = novel, onClick = { onNovelClick(novel) })
                    }
                    if (isLoadingMore || (hasMore && novels.isNotEmpty())) {
                        item(span = { GridItemSpan(2) }) {
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

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = filterSheetState,
        ) {
            FilterSheet(
                filters = filters,
                onApply = { applied ->
                    viewModel.applyFilters(applied)
                    showFilters = false
                },
                onDismiss = { showFilters = false },
            )
        }
    }
}

@Composable
private fun NovelCard(novel: Novel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            AsyncImage(
                model = novel.thumbnailUrl,
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            )
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

@Composable
private fun FilterSheet(
    filters: List<Filter<*>>,
    onApply: (List<Filter<*>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val localFilters = remember(filters) { filters.map { it.copy() } }

    Column(Modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onApply(localFilters) }) { Text("Apply") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
        HorizontalDivider()
        localFilters.forEach { filter -> FilterItem(filter) }
    }
}

@Composable
private fun FilterItem(filter: Filter<*>) {
    when (filter) {
        is Filter.Header -> Text(
            filter.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        is Filter.Separator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
        is Filter.CheckBox -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { filter.state = !filter.state }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = filter.state, onCheckedChange = { filter.state = it })
            Spacer(Modifier.width(8.dp))
            Text(filter.name)
        }
        is Filter.Select<*> -> Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(filter.name, style = MaterialTheme.typography.bodyMedium)
            filter.values.forEachIndexed { i, value ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filter.state = i }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = filter.state == i, onClick = { filter.state = i })
                    Spacer(Modifier.width(4.dp))
                    Text(value.toString())
                }
            }
        }
        else -> {} // Text, TriState, Group, Sort: omitted for now
    }
}

@Suppress("UNCHECKED_CAST")
private fun Filter<*>.copy(): Filter<*> = when (this) {
    is Filter.CheckBox -> Filter.CheckBox(name, state)
    is Filter.Header -> Filter.Header(name)
    is Filter.Separator -> Filter.Separator(name)
    else -> this
}
