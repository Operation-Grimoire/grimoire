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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesSeriesContent

@Composable
fun NovelUpdatesSection(
    state: NuInfoState,
    viewModel: NovelDetailViewModel,
    onOpenWebView: (String) -> Unit,
    onOpenNuSeries: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is NuInfoState.Idle || state is NuInfoState.Disabled) return

    val novel by viewModel.novel.collectAsState()
    var showLinkDialog by remember { mutableStateOf(false) }

    // Ambiguous matches already carry the candidates — open the picker straight
    // away instead of making the user tap a button and search again.
    LaunchedEffect(state) {
        if (state is NuInfoState.Ambiguous) showLinkDialog = true
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "NovelUpdates",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state is NuInfoState.Matched) {
                TextButton(onClick = { onOpenWebView(state.series.url) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Open")
                }
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

            is NuInfoState.Matched -> NovelUpdatesSeriesContent(
                series = state.series,
                onRecommendationClick = { url ->
                    onOpenNuSeries(NovelUpdatesEndpoints.slugFromUrl(url))
                },
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
            .padding(vertical = 12.dp),
    ) {
        AsyncImage(
            model = result.coverUrl,
            contentDescription = result.title,
            modifier = Modifier
                .size(68.dp, 96.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                result.rating?.let { r ->
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%.1f".format(r),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                result.language?.let { lang ->
                    if (result.rating != null) Spacer(Modifier.width(14.dp))
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = "Language",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        lang,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            result.stats?.let { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
