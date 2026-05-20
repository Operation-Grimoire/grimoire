package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.component.ExtensionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigateToManage: () -> Unit,
    onNavigateToSource: (packageName: String) -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToNovelUpdatesSearch: () -> Unit = {},
    onNavigateToNovelUpdatesRankings: () -> Unit = {},
    onNavigateToNovelUpdatesLatest: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val installed by viewModel.installed.collectAsState()

    val sourcesByLanguage = installed
        .sortedBy { it.name.lowercase() }
        .groupBy { it.lang.uppercase() }
        .toSortedMap()

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Browse") },
            actions = {
                IconButton(onClick = onNavigateToGlobalSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search all sources")
                }
                IconButton(onClick = onNavigateToManage) {
                    Icon(Icons.Default.Extension, contentDescription = "Manage extensions")
                }
            },
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "__nu_header__") { SectionHeader("NovelUpdates") }
            item(key = "__nu_search__") {
                NavRow(
                    icon = Icons.Default.Search,
                    title = "Search NovelUpdates",
                    subtitle = "Find a series by title",
                    onClick = onNavigateToNovelUpdatesSearch,
                )
            }
            item(key = "__nu_rankings__") {
                NavRow(
                    icon = Icons.Default.TrendingUp,
                    title = "Rankings",
                    subtitle = "Popular and most active series",
                    onClick = onNavigateToNovelUpdatesRankings,
                )
            }
            item(key = "__nu_latest__") {
                NavRow(
                    icon = Icons.Default.NewReleases,
                    title = "Latest releases",
                    subtitle = "Newest translated chapters",
                    onClick = onNavigateToNovelUpdatesLatest,
                )
                HorizontalDivider()
            }

            if (installed.isEmpty()) {
                item(key = "__empty__") {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No extensions installed\nTap the extension icon to add one",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                sourcesByLanguage.forEach { (lang, sources) ->
                    item(key = "__lang_$lang") { SectionHeader(languageLabel(lang)) }
                    items(sources, key = { it.packageName }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text(item.lang.uppercase()) },
                            leadingContent = { ExtensionIcon(item.packageName, item.lang) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToSource(item.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun languageLabel(code: String): String = when (code) {
    "EN" -> "English"
    "ALL" -> "Multi-language"
    else -> code
}
