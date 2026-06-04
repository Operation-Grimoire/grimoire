package io.grimoire.app.ui.screen.migrate

import androidx.compose.foundation.layout.Box
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.screen.browse.GlobalSearchResults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateScreen(
    onNavigateBack: () -> Unit,
    onPreviewNovel: (pkg: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MigrateViewModel = hiltViewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = {
                        keyboard?.hide()
                        onNavigateBack()
                    }, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    AppSearchField(
                        value = searchQuery,
                        onValueChange = viewModel::setQuery,
                        placeholder = "Search all sources…",
                        modifier = Modifier.fillMaxWidth(),
                        onSearch = { viewModel.submitSearch() },
                    )
                },
            )
        },
    ) { padding ->
        when {
            searchResults.isNotEmpty() -> GlobalSearchResults(
                results = searchResults,
                libraryKeys = emptySet(),
                onNovelClick = { novel, pkg -> onPreviewNovel(pkg, novel.url) },
                onSeeAll = {},
                modifier = Modifier.padding(padding),
                showSeeAll = false,
            )
            isSearching -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            searchQuery.isNotBlank() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No results found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Type to search all sources",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
