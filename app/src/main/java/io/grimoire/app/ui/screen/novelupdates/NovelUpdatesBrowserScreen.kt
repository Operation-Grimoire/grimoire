package io.grimoire.app.ui.screen.novelupdates

import androidx.compose.foundation.horizontalScroll
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.novelupdates.NuGenres
import io.grimoire.app.data.novelupdates.NuLanguages
import io.grimoire.app.data.novelupdates.NuListingFilter
import io.grimoire.app.data.novelupdates.NuRankingType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUpdatesBrowserScreen(
    onNavigateBack: () -> Unit,
    onSeriesClick: (slug: String) -> Unit,
    onOpenWebView: (url: String) -> Unit,
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
    val filter by viewModel.filter.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Text(if (mode == NuBrowseMode.RANKINGS) "Rankings" else "Latest")
            },
            actions = {
                PlainTooltipIconButton(onClick = { onOpenWebView(viewModel.currentPageUrl()) }, tooltip = "Open in WebView") {
                    Icon(Icons.Default.Language, contentDescription = "Open in WebView")
                }
                PlainTooltipIconButton(onClick = { showFilters = true }, tooltip = "Filters") {
                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                }
            },
        )

        if (mode == NuBrowseMode.RANKINGS) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NuRankingType.entries.forEach { type ->
                    FilterChip(
                        selected = rankingType == type,
                        onClick = { viewModel.setRankingType(type) },
                        label = { Text(type.label) },
                    )
                }
            }
        }

        NuResultsArea(
            results = results,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            error = error,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onSeriesClick = onSeriesClick,
            modifier = Modifier.weight(1f),
        )
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheetState) {
            ListingFilterSheet(
                current = filter,
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
private fun ListingFilterSheet(
    current: NuListingFilter,
    onApply: (NuListingFilter) -> Unit,
) {
    val genres = remember {
        current.genres.mapNotNull { id -> NuGenres.all.entries.firstOrNull { it.value == id }?.key }
            .toMutableStateList()
    }
    val languages = remember {
        current.languages.mapNotNull { id -> NuLanguages.all.entries.firstOrNull { it.value == id }?.key }
            .toMutableStateList()
    }
    var matchAll by remember { mutableStateOf(current.genresMatchAll) }

    fun toggle(list: MutableList<String>, v: String) { if (!list.remove(v)) list.add(v) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Genres", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilterChip(
                selected = matchAll,
                onClick = { matchAll = !matchAll },
                label = { Text(if (matchAll) "Match ALL" else "Match ANY") },
            )
        }
        ChipFlow {
            NuGenres.all.keys.forEach { name ->
                FilterChip(
                    selected = name in genres,
                    onClick = { toggle(genres, name) },
                    label = { Text(name) },
                )
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
                onApply(
                    NuListingFilter(
                        languages = languages.mapNotNull { NuLanguages.all[it] },
                        genres = genres.mapNotNull { NuGenres.all[it] },
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
internal fun ChipFlow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}
