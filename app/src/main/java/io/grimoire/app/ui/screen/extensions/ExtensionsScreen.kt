package io.grimoire.app.ui.screen.extensions

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
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
import io.grimoire.api.source.MultiLanguageSource
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.ui.component.ExtensionIcon
import io.grimoire.app.ui.component.LinkText
import io.grimoire.app.util.languageLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenSourceSettings: (pkg: String) -> Unit = {},
    onConnectGitHub: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val installStates by viewModel.installStates.collectAsState()
    val authRequiredRepos by viewModel.authRequiredRepos.collectAsState()
    val githubLogin by viewModel.githubLogin.collectAsState()

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
    var pendingRemove by remember { mutableStateOf<ExtensionItem?>(null) }
    val repoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Extensions") },
                actions = {
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
                },
            )
        },
    ) { padding ->
        if (installed.isEmpty() && available.isEmpty()) {
            EmptyState("No extensions found\nAdd a repository to discover extensions", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (authRequiredRepos.isNotEmpty()) {
                    item {
                        AuthRequiredBanner(
                            repoNames = authRequiredRepos.map { it.name },
                            signedInAs = githubLogin,
                            onConnect = onConnectGitHub,
                        )
                    }
                }
                if (installed.isNotEmpty()) {
                    item {
                        SectionHeader("Installed")
                    }
                    items(installed, key = { it.packageName }) { item ->
                        val state = installStates[item.packageName]
                        val hasUpdate = item is ExtensionItem.Installed && item.hasUpdate
                        val languages = item.multiLanguageOptions()
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = {
                                Column {
                                    Text("${languageLabel(item.lang)} · v${item.versionName}")
                                    if (!languages.isNullOrEmpty()) {
                                        Text(
                                            multiLanguageSummary(languages),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (hasUpdate && state == null) {
                                        Text(
                                            "Update available: v${(item as ExtensionItem.Installed).remoteVersionName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    InstallProgressRow(state)
                                }
                            },
                            leadingContent = {
                                ExtensionIcon(item.packageName, item.lang, item.iconUrl)
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    when {
                                        state is InstallState.Downloading -> {
                                            Text(
                                                text = "${downloadPercent(state)}%",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                        state is InstallState.Error -> {
                                            OutlinedButton(onClick = {
                                                if (item is ExtensionItem.Installed) viewModel.update(item)
                                                else viewModel.dismissInstallError(item.packageName)
                                            }) { Text("Retry") }
                                        }
                                        hasUpdate -> {
                                            OutlinedButton(onClick = {
                                                viewModel.update(item as ExtensionItem.Installed)
                                            }) { Text("Update") }
                                        }
                                    }
                                    IconButton(onClick = { onOpenSourceSettings(item.packageName) }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                    IconButton(onClick = { pendingRemove = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                                    }
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
                            supportingContent = {
                                Column {
                                    Text("${languageLabel(item.lang)} · v${item.versionName}")
                                    InstallProgressRow(state)
                                }
                            },
                            leadingContent = {
                                ExtensionIcon(item.packageName, item.lang, item.iconUrl)
                            },
                            trailingContent = {
                                when (state) {
                                    is InstallState.Downloading -> {
                                        Text(
                                            text = "${downloadPercent(state)}%",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                    is InstallState.Error -> {
                                        OutlinedButton(onClick = { viewModel.install(item) }) {
                                            Text("Retry")
                                        }
                                    }
                                    null -> {
                                        IconButton(onClick = { viewModel.install(item) }) {
                                            Icon(Icons.Default.Download, contentDescription = "Install")
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

    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove extension?") },
            text = { Text("Remove ${item.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    @Suppress("DEPRECATION")
                    context.startActivity(
                        Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                            data = android.net.Uri.parse("package:${item.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InstallProgressRow(state: InstallState?) {
    when (state) {
        is InstallState.Downloading -> {
            val total = state.totalBytes
            Column {
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.bytesRead.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
                Text(
                    text = downloadLabel(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is InstallState.Error -> {
            Text(
                state.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        null -> {}
    }
}

private fun downloadPercent(state: InstallState.Downloading): Int =
    if (state.totalBytes > 0) (state.bytesRead * 100 / state.totalBytes).toInt().coerceIn(0, 100) else 0

private fun downloadLabel(state: InstallState.Downloading): String {
    val mb: (Long) -> String = { "%.1f".format(it / 1024.0 / 1024.0) }
    return if (state.totalBytes > 0) {
        "${mb(state.bytesRead)} / ${mb(state.totalBytes)} MB · ${downloadPercent(state)}%"
    } else {
        "${mb(state.bytesRead)} MB"
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
private fun AuthRequiredBanner(
    repoNames: List<String>,
    signedInAs: String?,
    onConnect: () -> Unit,
) {
    val joinedNames = repoNames.joinToString(", ")
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (signedInAs == null) {
                Text(
                    if (repoNames.size == 1) "Sign-in required for ${repoNames.first()}"
                    else "Sign-in required for $joinedNames",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "These look like private GitHub repositories. Connect a GitHub account to load them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onConnect) { Text("Connect GitHub") }
            } else {
                Text(
                    if (repoNames.size == 1) "No access to ${repoNames.first()}"
                    else "No access to $joinedNames",
                    style = MaterialTheme.typography.titleSmall,
                )
                LinkText(
                    text = "Signed in as @$signedInAs, but this OAuth app hasn't been granted access " +
                        "to the owning organization yet. Approve it at github.com/settings/applications, " +
                        "then pull to refresh.",
                    "github.com/settings/applications" to "https://github.com/settings/applications",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onConnect) { Text("Manage GitHub connection") }
            }
        }
    }
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

private fun ExtensionItem.multiLanguageOptions(): List<String>? = when (this) {
    is ExtensionItem.Installed -> (loaded.source as? MultiLanguageSource)?.availableLanguages()
    is ExtensionItem.InstalledOnly -> (loaded.source as? MultiLanguageSource)?.availableLanguages()
    is ExtensionItem.Available -> null
}

private const val MULTI_LANGUAGE_PREVIEW_COUNT = 3

private fun multiLanguageSummary(languages: List<String>): String {
    val shown = languages.take(MULTI_LANGUAGE_PREVIEW_COUNT)
    val extra = languages.size - shown.size
    return shown.joinToString(", ") + if (extra > 0) " + $extra more" else ""
}
