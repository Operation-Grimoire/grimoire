package io.grimoire.app.ui.screen.more.statistics

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.Arrangement
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.R
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
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            item { SectionHeader(stringResource(R.string.statistics_reading)) }
            item {
                StatPairRow(
                    StatTile(stringResource(R.string.statistics_chapters_read), reading.chaptersRead.formatted()),
                    StatTile(stringResource(R.string.statistics_words_read), reading.wordsRead.formatted()),
                )
            }
            item {
                StatPairRow(
                    StatTile(stringResource(R.string.statistics_novels_completed), reading.novelsCompleted.formatted()),
                    StatTile(stringResource(R.string.statistics_novels_started), reading.novelsStarted.formatted()),
                )
            }
            item {
                val avg = if (reading.chaptersRead > 0)
                    (reading.wordsRead / reading.chaptersRead).formatted() else "–"
                StatPairRow(
                    StatTile(stringResource(R.string.statistics_average_words), avg),
                    StatTile(stringResource(R.string.statistics_last_read), reading.lastReadAt.formatDate()),
                )
            }

            item { SectionHeader(stringResource(R.string.statistics_library)) }
            item {
                StatPairRow(
                    StatTile(stringResource(R.string.statistics_library_novels), library.favoriteNovels.formatted()),
                    StatTile(stringResource(R.string.statistics_library_chapters), library.libraryChapters.formatted()),
                )
            }
            item {
                StatPairRow(
                    StatTile(stringResource(R.string.statistics_library_unread), library.libraryUnreadChapters.formatted()),
                    StatTile(stringResource(R.string.statistics_downloaded), library.downloadedChapters.formatted()),
                )
            }

            if (reading.chaptersRead == 0) {
                item {
                    Text(
                        text = stringResource(R.string.statistics_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.statistics_counting_hint),
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
