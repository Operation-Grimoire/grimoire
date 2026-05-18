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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                                if (series.artists.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Art: " + series.artists.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val typeLine = listOfNotNull(
                                    series.type,
                                    series.language,
                                    series.year,
                                ).distinct().joinToString(" · ")
                                if (typeLine.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        typeLine,
                                        style = MaterialTheme.typography.bodySmall,
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

                        NuDetails(series)

                        if (series.releases.isNotEmpty()) {
                            NuReleases(
                                releases = series.releases,
                                onMore = { onOpenWebView(series.url) },
                            )
                        }

                        if (series.reviews.isNotEmpty()) {
                            NuReviews(
                                reviews = series.reviews,
                                reviewCount = series.reviewCount,
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
private fun NuDetails(series: io.grimoire.app.data.novelupdates.NuSeries) {
    val rows = buildList {
        series.year?.let { add("Year" to it) }
        series.releaseFrequency?.let { add("Release frequency" to it) }
        series.licensed?.let { add("Licensed" to if (it) "Yes" else "No") }
        series.completelyTranslated?.let {
            add("Completely translated" to if (it) "Yes" else "No")
        }
        if (series.originalPublishers.isNotEmpty()) {
            add("Original publisher" to series.originalPublishers.joinToString(" · "))
        }
        if (series.englishPublishers.isNotEmpty()) {
            add("English publisher" to series.englishPublishers.joinToString(" · "))
        }
        series.readingListCount?.let {
            add("Reading lists" to "%,d".format(it))
        }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Details", style = MaterialTheme.typography.titleSmall)
        rows.forEach { (label, value) ->
            Row {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(140.dp),
                )
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NuReleases(
    releases: List<io.grimoire.app.data.novelupdates.NuRelease>,
    onMore: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) releases else releases.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Latest releases", style = MaterialTheme.typography.titleSmall)
        shown.forEach { rel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rel.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp),
                )
                Text(
                    rel.chapter,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    rel.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (releases.size > 5) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show all ${releases.size}")
            }
        }
        TextButton(onClick = onMore) {
            Text("View all on NovelUpdates")
        }
    }
}

@Composable
private fun NuReviews(
    reviews: List<NuReview>,
    reviewCount: Int?,
    pageCount: Int,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            reviewCount?.let { "Reviews ($it)" } ?: "Reviews",
            style = MaterialTheme.typography.titleSmall,
        )
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
