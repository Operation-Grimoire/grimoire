package io.grimoire.app.ui.screen.more.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val reading by viewModel.readingStats.collectAsState()
    val library by viewModel.libraryStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Reading") }
            item {
                StatPairRow(
                    StatTile("Chapters read", reading.chaptersRead.formatted()),
                    StatTile("Words read", reading.wordsRead.formatted()),
                )
            }
            item {
                StatPairRow(
                    StatTile("Novels completed", reading.novelsCompleted.formatted()),
                    StatTile("Novels started", reading.novelsStarted.formatted()),
                )
            }
            item {
                val avg = if (reading.chaptersRead > 0)
                    (reading.wordsRead / reading.chaptersRead).formatted() else "–"
                StatPairRow(
                    StatTile("Avg words / chapter", avg),
                    StatTile("Last read", reading.lastReadAt.formatDate()),
                )
            }

            item { SectionHeader("Library") }
            item {
                StatPairRow(
                    StatTile("Novels in library", library.favoriteNovels.formatted()),
                    StatTile("Library chapters", library.libraryChapters.formatted()),
                )
            }
            item {
                StatPairRow(
                    StatTile("Unread in library", library.libraryUnreadChapters.formatted()),
                    StatTile("Downloaded", library.downloadedChapters.formatted()),
                )
            }

            if (reading.chaptersRead == 0) {
                item {
                    Text(
                        text = "Read some chapters to start tracking statistics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item {
                    Text(
                        text = "Stats count each chapter once — marking unread doesn't subtract.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private data class StatTile(val label: String, val value: String)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatPairRow(left: StatTile, right: StatTile) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(left, Modifier.weight(1f))
        StatCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(tile: StatTile, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = tile.value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tile.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Int.formatted(): String = "%,d".format(this)
private fun Long.formatted(): String = "%,d".format(this)

private fun Long?.formatDate(): String {
    val ts = this ?: return "–"
    if (ts <= 0L) return "–"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
}
