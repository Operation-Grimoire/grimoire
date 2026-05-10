package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.model.Novel
import io.grimoire.app.ui.component.ExtensionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigateToManage: () -> Unit,
    onNavigateToSource: (packageName: String) -> Unit = {},
    onNovelClick: (Novel, sourcePkg: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val installed by viewModel.installed.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    androidx.compose.foundation.layout.Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                if (searchActive) {
                    IconButton(onClick = {
                        searchActive = false
                        viewModel.clearSearch()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                }
            },
            title = {
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search all sources…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            viewModel.submitSearch()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                    )
                } else {
                    Text("Browse")
                }
            },
            actions = {
                if (!searchActive) {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search all sources")
                    }
                }
                IconButton(onClick = onNavigateToManage) {
                    Icon(Icons.Default.Extension, contentDescription = "Manage extensions")
                }
            },
        )

        when {
            searchActive && isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            searchActive && searchResults.isNotEmpty() -> GlobalSearchResults(
                results = searchResults,
                onNovelClick = onNovelClick,
            )

            searchActive && searchQuery.isNotBlank() && !isSearching -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No results found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            installed.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No extensions installed\nTap the extension icon to add one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(installed, key = { it.packageName }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text(item.lang.uppercase()) },
                        leadingContent = { ExtensionIcon(item.packageName, item.lang) },
                        modifier = Modifier.clickable { onNavigateToSource(item.packageName) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResults(
    results: List<GlobalSearchResult>,
    onNovelClick: (Novel, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        results.forEach { group ->
            item(key = "header_${group.packageName}") {
                Text(
                    text = group.sourceName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(
                items = group.novels.take(5),
                key = { "${group.packageName}_${it.url}" },
            ) { novel ->
                ListItem(
                    headlineContent = {
                        Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier
                        .clickable { onNovelClick(novel, group.packageName) }
                        .padding(start = 8.dp),
                )
            }
            item(key = "divider_${group.packageName}") { HorizontalDivider() }
        }
    }
}
