package io.grimoire.app.ui.screen.migrate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.model.Novel
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.screen.browse.GlobalSearchResults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrateScreen(
    onNavigateBack: () -> Unit,
    onMigrated: (pkg: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MigrateViewModel = hiltViewModel(),
) {
    val sourceTitle by viewModel.sourceTitle.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val migrationState by viewModel.migrationState.collectAsState()

    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingTarget by remember { mutableStateOf<Pair<Novel, String>?>(null) }

    LaunchedEffect(migrationState) {
        when (val state = migrationState) {
            is MigrationState.Success -> onMigrated(state.targetPkg, state.targetUrl)
            is MigrationState.Error -> snackbarHostState.showSnackbar("Migration failed: ${state.message}")
            else -> Unit
        }
    }

    pendingTarget?.let { (target, pkg) ->
        AlertDialog(
            onDismissRequest = { pendingTarget = null },
            title = { Text("Migrate novel?") },
            text = {
                Text(
                    "Your read progress will be moved to \"${target.title}\". " +
                        "\"$sourceTitle\" will be removed from your library.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTarget = null
                    viewModel.migrate(target, pkg)
                }) { Text("Migrate") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTarget = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        keyboard?.hide()
                        onNavigateBack()
                    }) {
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                searchResults.isNotEmpty() -> GlobalSearchResults(
                    results = searchResults,
                    libraryKeys = emptySet(),
                    onNovelClick = { novel, pkg -> pendingTarget = novel to pkg },
                    onSeeAll = {},
                    showSeeAll = false,
                )
                isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                searchQuery.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Type to search all sources",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (migrationState == MigrationState.Running) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Migrating…", color = Color.White)
                    }
                }
            }
        }
    }
}
