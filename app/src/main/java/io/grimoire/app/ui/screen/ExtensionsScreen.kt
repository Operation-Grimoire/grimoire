package io.grimoire.app.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val installStates by viewModel.installStates.collectAsState()

    val installed = items.filterIsInstance<ExtensionItem.Installed>() +
            items.filterIsInstance<ExtensionItem.InstalledOnly>()
    val available = items.filterIsInstance<ExtensionItem.Available>()

    val context = LocalContext.current
    val pendingInstall by viewModel.pendingInstall.collectAsState()

    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onInstallResult() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> wasPaused = true
                Lifecycle.Event.ON_RESUME -> if (wasPaused) { wasPaused = false; viewModel.refresh() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingInstall) {
        pendingInstall?.let { file ->
            viewModel.consumePendingInstall()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            installLauncher.launch(intent)
        }
    }

    var showRepos by remember { mutableStateOf(false) }
    var showAddRepo by remember { mutableStateOf(false) }
    var editRepo by remember { mutableStateOf<RepoEntity?>(null) }
    val repoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Extensions",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (isFetching) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            IconButton(onClick = { showRepos = true }) {
                Icon(Icons.Default.Storage, contentDescription = "Repositories")
            }
        }

        HorizontalDivider()

        if (installed.isEmpty() && available.isEmpty()) {
            EmptyState("No extensions found\nAdd a repository to discover extensions")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (installed.isNotEmpty()) {
                    item {
                        SectionHeader("Installed")
                    }
                    items(installed, key = { it.packageName }) { item ->
                        val state = installStates[item.packageName]
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = {
                                Column {
                                    Text("${item.lang.uppercase()} · v${item.versionName}")
                                    if (item is ExtensionItem.Installed && item.hasUpdate) {
                                        Text(
                                            "Update available: v${item.remoteVersionName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            leadingContent = { ExtensionIcon(item.packageName, item.lang) },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (item is ExtensionItem.Installed && item.hasUpdate) {
                                        if (state == InstallState.DOWNLOADING) {
                                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            OutlinedButton(onClick = { viewModel.update(item) }) { Text("Update") }
                                        }
                                    }
                                    TextButton(onClick = {
                                        @Suppress("DEPRECATION")
                                        context.startActivity(
                                            Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                                data = android.net.Uri.parse("package:${item.packageName}")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    }) { Text("Remove") }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }

                if (available.isNotEmpty()) {
                    item {
                        SectionHeader("Available")
                    }
                    items(available, key = { it.packageName }) { item ->
                        val state = installStates[item.packageName]
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text("${item.lang.uppercase()} · v${item.versionName}") },
                            leadingContent = { LangBadge(item.lang) },
                            trailingContent = {
                                if (state == InstallState.DOWNLOADING) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Button(onClick = { viewModel.install(item) }) { Text("Install") }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showRepos) {
        ModalBottomSheet(
            onDismissRequest = { showRepos = false },
            sheetState = repoSheetState,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Repositories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showAddRepo = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add repository")
                }
            }
            HorizontalDivider()
            if (repos.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No repositories\nTap + to add one",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(repos, key = { it.id }) { repo ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(repo.name) },
                            supportingContent = {
                                Text(
                                    repo.indexUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = repo.enabled,
                                        onCheckedChange = { viewModel.toggleRepo(repo) },
                                    )
                                    Box {
                                        IconButton(onClick = { menuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit") },
                                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                onClick = { menuExpanded = false; editRepo = repo },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                                onClick = { menuExpanded = false; viewModel.deleteRepo(repo) },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddRepo) {
        RepoDialog(
            initial = null,
            onConfirm = { name, url -> viewModel.addRepo(name, url); showAddRepo = false },
            onDismiss = { showAddRepo = false },
        )
    }

    editRepo?.let { repo ->
        RepoDialog(
            initial = repo,
            onConfirm = { name, url -> viewModel.updateRepo(repo, name, url); editRepo = null },
            onDismiss = { editRepo = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RepoDialog(
    initial: RepoEntity?,
    onConfirm: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.indexUrl ?: "") }
    val urlError = url.isNotEmpty() && (!url.startsWith("http") || !url.endsWith(".json"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Repository" else "Edit Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Index URL") },
                    placeholder = { Text("https://example.com/index.json") },
                    singleLine = true,
                    isError = urlError,
                    supportingText = if (urlError) ({ Text("Must be an http(s) URL ending in .json") }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank() && !urlError,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
