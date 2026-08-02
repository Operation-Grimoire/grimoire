package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.NovelQuickViewSheet
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.api.model.novel.Novel
import androidx.compose.material3.MaterialTheme
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onNavigateBack: () -> Unit,
    onNovelClick: (Novel, sourcePkg: String) -> Unit = { _, _ -> },
    onChapterClick: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit = { _, _, _ -> },
    onNavigateToSourceSearch: (packageName: String, query: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.sortedSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val libraryKeys by viewModel.libraryKeys.collectAsState()
    val pinned by viewModel.pinnedPackages.collectAsState()
    val searchedAll by viewModel.searchedAllSources.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var quickView by remember { mutableStateOf<Pair<Novel, String>?>(null) }

    // Hoisted so the submit scope and the lazy widening below can read the
    // active tab. Land on Pinned when the user curates pins; otherwise With
    // results is the useful default.
    val pagerState = rememberPagerState(
        initialPage = if (pinned.isEmpty()) 1 else 0,
        pageCount = { 3 },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // A search submitted from the Pinned tab only queried pinned sources; the
    // first visit to a wider tab sends the query to the rest.
    LaunchedEffect(pagerState.currentPage, searchResults.isNotEmpty()) {
        if (pagerState.currentPage >= 1 && searchResults.isNotEmpty()) {
            viewModel.extendSearchToAllSources()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = {
                        keyboard?.hide()
                        viewModel.clearSearch()
                        onNavigateBack()
                    }, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    AppSearchField(
                        value = searchQuery,
                        onValueChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.global_search_all_placeholder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        onSearch = { viewModel.submitSearch(pinnedOnly = pagerState.currentPage == 0) },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize()) {
                when {
                    // Three swipeable views over the same sorted results:
                    // pinned sources, sources that returned hits, everything.
                    searchResults.isNotEmpty() -> SwipeTabRow(
                        tabs = listOf(
                            stringResource(R.string.global_search_tab_pinned),
                            stringResource(R.string.global_search_tab_results),
                            stringResource(R.string.global_search_tab_all),
                        ),
                        // Sources-with-hits counts; the wider tabs only earn a
                        // badge once their scope has actually been searched.
                        badges = run {
                            val hits = withResultsOnly(searchResults)
                            val pinnedHits = hits.count { it.packageName in pinned }
                            listOf(
                                if (pinned.isEmpty()) null else "$pinnedHits",
                                if (searchedAll) "${hits.size}" else null,
                                if (searchedAll) "${hits.size}" else null,
                            )
                        },
                        pagerState = pagerState,
                        style = SwipeTabStyle.Secondary,
                    ) { page ->
                        val pageResults = when (page) {
                            0 -> searchResults.filter { it.packageName in pinned }
                            1 -> withResultsOnly(searchResults)
                            else -> searchResults
                        }
                        if (pageResults.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when {
                                    page == 0 && pinned.isEmpty() -> Text(
                                        stringResource(R.string.global_search_scope_tip),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 32.dp),
                                    )
                                    isSearching -> CircularProgressIndicator()
                                    else -> Text(
                                        stringResource(R.string.global_search_no_results_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            GlobalSearchResults(
                                results = pageResults,
                                libraryKeys = libraryKeys,
                                onNovelClick = onNovelClick,
                                onNovelLongClick = { novel, pkg -> quickView = novel to pkg },
                                onSeeAll = { pkg -> onNavigateToSourceSearch(pkg, searchQuery) },
                            )
                        }
                    }
                    isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    searchQuery.isNotBlank() && !isSearching -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.global_search_no_results_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.global_search_type_all),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    quickView?.let { (novel, pkg) ->
        NovelQuickViewSheet(
            packageName = pkg,
            novelUrl = novel.url,
            onOpenDetails = { onNovelClick(novel, pkg) },
            onChapterClick = { chapterUrl -> onChapterClick(pkg, novel.url, chapterUrl) },
            onDismiss = { quickView = null },
        )
    }
}

@Composable
internal fun GlobalSearchResults(
    results: List<GlobalSearchResult>,
    libraryKeys: Set<Pair<Long, String>>,
    onNovelClick: (Novel, String) -> Unit,
    onNovelLongClick: (Novel, String) -> Unit = { _, _ -> },
    onSeeAll: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    showSeeAll: Boolean = true,
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
                    if (showSeeAll && !group.isLoading && group.novels.isNotEmpty()) {
                        TextButton(onClick = { onSeeAll(group.packageName) }) {
                            Text(stringResource(R.string.global_search_see_all))
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
                        text = stringResource(R.string.global_search_no_results),
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
                                inLibrary = (group.sourceId to novel.url) in libraryKeys,
                                onClick = { onNovelClick(novel, group.packageName) },
                                onLongClick = { onNovelLongClick(novel, group.packageName) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelCoverCard(
    novel: Novel,
    inLibrary: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                        AppIcons.Bookmark,
                        contentDescription = stringResource(R.string.content_description_in_library),
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
