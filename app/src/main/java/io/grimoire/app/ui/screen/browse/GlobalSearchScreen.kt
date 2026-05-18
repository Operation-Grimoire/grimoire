package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.grimoire.app.ui.component.AppSearchField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.api.model.Novel
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onNavigateBack: () -> Unit,
    onNovelClick: (Novel, sourcePkg: String) -> Unit = { _, _ -> },
    onNavigateToSourceSearch: (packageName: String, query: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val libraryUrls by viewModel.libraryUrls.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        keyboard?.hide()
                        viewModel.clearSearch()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    AppSearchField(
                        value = searchQuery,
                        onValueChange = viewModel::setQuery,
                        placeholder = "Search all sources…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        onSearch = { viewModel.submitSearch() },
                    )
                },
            )
        },
    ) { padding ->
        when {
            searchResults.isNotEmpty() -> GlobalSearchResults(
                results = searchResults,
                libraryUrls = libraryUrls,
                onNovelClick = onNovelClick,
                onSeeAll = { pkg -> onNavigateToSourceSearch(pkg, searchQuery) },
                modifier = Modifier.padding(padding),
            )
            isSearching -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            searchQuery.isNotBlank() && !isSearching -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No results found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Type to search all sources",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun GlobalSearchResults(
    results: List<GlobalSearchResult>,
    libraryUrls: Set<String>,
    onNovelClick: (Novel, String) -> Unit,
    onSeeAll: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        results.forEach { group ->
            item(key = "header_${group.packageName}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = group.sourceName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!group.isLoading && group.novels.isNotEmpty()) {
                        TextButton(onClick = { onSeeAll(group.packageName) }) {
                            Text("See all")
                        }
                    }
                }
            }

            when {
                group.isLoading -> item(key = "loading_${group.packageName}") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                group.error != null -> item(key = "error_${group.packageName}") {
                    Text(
                        text = group.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                group.novels.isEmpty() -> item(key = "empty_${group.packageName}") {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                else -> item(key = "row_${group.packageName}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(group.novels.take(10), key = { it.url }) { novel ->
                            NovelCoverCard(
                                novel = novel,
                                inLibrary = novel.url in libraryUrls,
                                onClick = { onNovelClick(novel, group.packageName) },
                            )
                        }
                    }
                }
            }

            item(key = "divider_${group.packageName}") {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
internal fun NovelCoverCard(novel: Novel, inLibrary: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            AsyncImage(
                model = novel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            if (inLibrary) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
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
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
