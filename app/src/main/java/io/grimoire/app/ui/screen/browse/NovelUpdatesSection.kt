package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.data.novelupdates.NuSeries

@Composable
fun NovelUpdatesSection(
    state: NuInfoState,
    viewModel: NovelDetailViewModel,
    onOpenWebView: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is NuInfoState.Idle || state is NuInfoState.Disabled) return

    val novel by viewModel.novel.collectAsState()
    var showLinkDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NovelUpdates",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (state is NuInfoState.Matched) {
                TextButton(onClick = { onOpenWebView(state.series.url) }) { Text("Open") }
            }
        }

        when (state) {
            is NuInfoState.NotLoaded ->
                TextButton(
                    onClick = { viewModel.loadNovelUpdates() },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Load from NovelUpdates") }

            is NuInfoState.Loading ->
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }

            is NuInfoState.Matched -> MatchedContent(
                series = state.series,
                onRecommendationClick = { onOpenWebView(it) },
                onRelink = { showLinkDialog = true },
            )

            is NuInfoState.Ambiguous -> Column {
                Text(
                    "Multiple NovelUpdates matches.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = { showLinkDialog = true },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Choose the right one") }
            }

            is NuInfoState.NotFound -> Column {
                Text(
                    "No NovelUpdates match found.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = { showLinkDialog = true },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Link manually") }
            }

            is NuInfoState.Error -> Column {
                Text(
                    "NovelUpdates unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row {
                    TextButton(
                        onClick = { viewModel.retryNovelUpdates() },
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("Retry") }
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        onClick = { showLinkDialog = true },
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("Link manually") }
                }
            }

            else -> Unit
        }
    }

    if (showLinkDialog) {
        LinkDialog(
            initialQuery = (state as? NuInfoState.Matched)?.series?.title
                ?: novel.title,
            viewModel = viewModel,
            onDismiss = { showLinkDialog = false },
            onPick = {
                viewModel.linkNovelUpdates(it.slug)
                showLinkDialog = false
            },
        )
    }
}

@Composable
private fun MatchedContent(
    series: NuSeries,
    onRecommendationClick: (String) -> Unit,
    onRelink: () -> Unit,
) {
    Column {
        if (series.associatedNames.isNotEmpty()) {
            Text(
                "Also known as: " + series.associatedNames.take(4).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val facts = buildList {
            series.status?.let { add(it) }
            series.rating?.let { r ->
                add("★ %.1f".format(r) + (series.ratingVotes?.let { " ($it)" } ?: ""))
            }
        }
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString("   "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (series.recommendations.isNotEmpty()) {
            Text(
                "Recommended",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(series.recommendations, key = { it.url }) { rec ->
                    Column(
                        Modifier
                            .width(96.dp)
                            .clickable { onRecommendationClick(rec.url) },
                    ) {
                        AsyncImage(
                            model = rec.coverUrl,
                            contentDescription = rec.title,
                            modifier = Modifier
                                .width(96.dp)
                                .height(132.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Text(
                            rec.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onRelink,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Not the right series?") }
    }
}

@Composable
private fun LinkDialog(
    initialQuery: String,
    viewModel: NovelDetailViewModel,
    onDismiss: () -> Unit,
    onPick: (NuSearchResult) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    val results by viewModel.nuSearchResults.collectAsState()
    val searching by viewModel.nuSearching.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Link NovelUpdates",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search NovelUpdates") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { viewModel.searchNovelUpdates(query) }) {
                            Text("Search")
                        }
                    },
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.searchNovelUpdates(query) },
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    searching -> Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }

                    results.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        Alignment.Center,
                    ) {
                        Text(
                            "No results — try a shorter or different title.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results, key = { it.url }) { result ->
                            SearchResultRow(result) { onPick(result) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: NuSearchResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.coverUrl,
            contentDescription = result.title,
            modifier = Modifier
                .size(56.dp, 80.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                result.rating?.let { add("★ %.1f".format(it)) }
                result.language?.let { add(it) }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            result.stats?.let { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
