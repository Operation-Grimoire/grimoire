package io.grimoire.app.ui.screen.novelupdates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
import io.grimoire.app.data.novelupdates.NuReview
import io.grimoire.app.ui.component.ExpandableText
import io.grimoire.app.ui.component.GenreChips
import io.grimoire.app.ui.component.ZoomableCoverImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUpdatesSeriesScreen(
    onNavigateBack: () -> Unit,
    onFindInSources: (title: String) -> Unit,
    onOpenSeries: (slug: String) -> Unit,
    onOpenWebView: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovelUpdatesSeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

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
                    val s = state
                    if (s is NuSeriesState.Loaded) {
                        IconButton(onClick = { onOpenWebView(s.series.url) }) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Open in WebView",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is NuSeriesState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center,
                ) { CircularProgressIndicator() }

                is NuSeriesState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
                }

                is NuSeriesState.Loaded -> {
                    val series = s.series

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZoomableCoverImage(
                                model = series.coverUrl,
                                contentDescription = series.title,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(156.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (series.authors.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        series.authors.joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (series.genres.isNotEmpty()) {
                            GenreChips(genres = series.genres)
                        }

                        series.description?.let { desc ->
                            Text("Description", style = MaterialTheme.typography.titleSmall)
                            ExpandableText(text = desc)
                        }

                        Button(
                            onClick = { onFindInSources(series.title) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Find in my sources")
                        }

                        NovelUpdatesSeriesContent(
                            series = series,
                            onRecommendationClick = { url ->
                                onOpenSeries(NovelUpdatesEndpoints.slugFromUrl(url))
                            },
                        )

                        if (series.reviews.isNotEmpty()) {
                            NuReviews(
                                reviews = series.reviews,
                                pageCount = series.reviewPageCount,
                                onMore = { onOpenWebView(series.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NuReviews(
    reviews: List<NuReview>,
    pageCount: Int,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reviews", style = MaterialTheme.typography.titleSmall)
        reviews.forEach { review ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        review.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    review.rating?.let { stars ->
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("$stars/5", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val meta = listOfNotNull(
                    review.date,
                    review.progress?.let { "Progress $it" },
                    review.likes?.let { "$it likes" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpandableText(text = review.body, collapsedMaxLines = 4)
            }
            HorizontalDivider()
        }
        if (pageCount > 1) {
            TextButton(onClick = onMore) {
                Text("More reviews on NovelUpdates")
            }
        }
    }
}
