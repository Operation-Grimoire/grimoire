package io.grimoire.app.ui.screen.novelupdates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.app.data.novelupdates.NuSeries

/**
 * Shared presentation of a NovelUpdates series (rating, status, associated
 * names, recommendations). Used both by the novel-detail NovelUpdates section
 * and the standalone NovelUpdates series screen.
 *
 * @param onRelink optional "not the right series?" affordance — only the
 *   detail-screen section passes it; the standalone browser omits it.
 */
@Composable
fun NovelUpdatesSeriesContent(
    series: NuSeries,
    onRecommendationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRelink: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        series.rating?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Rating",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "%.1f".format(r) + (series.ratingVotes?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        series.status?.let { st ->
            val segments = st.split("\n", " -")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                Column {
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    segments.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (series.associatedNames.isNotEmpty()) {
            Column {
                Text(
                    "Also known as",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    series.associatedNames.take(4).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (series.recommendations.isNotEmpty()) {
            Column {
                Text(
                    "Recommended",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(series.recommendations, key = { it.url }) { rec ->
                        Column(
                            Modifier
                                .width(104.dp)
                                .clickable { onRecommendationClick(rec.url) },
                        ) {
                            AsyncImage(
                                model = rec.coverUrl,
                                contentDescription = rec.title,
                                modifier = Modifier
                                    .width(104.dp)
                                    .height(146.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Text(
                                rec.title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (onRelink != null) {
            TextButton(
                onClick = onRelink,
                contentPadding = PaddingValues(0.dp),
            ) { Text("Not the right series?") }
        }
    }
}
