package io.grimoire.app.ui.screen.extensions

import android.content.Intent
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.source.ConfigurableSource
import io.grimoire.api.source.MultiHostSource
import io.grimoire.api.source.MultiLanguageSource
import io.grimoire.api.source.WebViewLoginSource
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.ui.PendingAddRepo
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.LanguageFilterChips
import io.grimoire.app.ui.component.LinkText
import io.grimoire.app.ui.component.SourceListItem
import io.grimoire.app.util.languageLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenSourceSettings: (pkg: String) -> Unit = {},
    onConnectGitHub: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExtensionsViewModel = hiltViewModel(),
    pendingAddRepo: StateFlow<PendingAddRepo?> = MutableStateFlow(null),
    onAddRepoHandled: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val installStates by viewModel.installStates.collectAsState()
    val authRequiredRepos by viewModel.authRequiredRepos.collectAsState()
    val githubLogin by viewModel.githubLogin.collectAsState()
    val rateLimitPrompt by viewModel.rateLimitPrompt.collectAsState()
    val nameFilter by viewModel.nameFilter.collectAsState()
    val languageFilter by viewModel.languageFilter.collectAsState()
    val section by viewModel.section.collectAsState()

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
    var addRepoPrefill by remember { mutableStateOf<PendingAddRepo?>(null) }
    var pendingRemove by remember { mutableStateOf<ExtensionItem?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val repoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filterSheetState = rememberModalBottomSheetState()
    val filtersActive = section != ExtensionSection.ALL || languageFilter != null

    val exitSearch = {
        searchActive = false
        viewModel.setNameFilter("")
    }
    BackHandler(enabled = searchActive) { exitSearch() }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    LaunchedEffect(pendingAddRepo) {
        // Collect the flow directly rather than reacting to a collectAsState
        // value change — when the activity comes back via onNewIntent while
        // Extensions is already on top, the State<T> re-emission isn't
        // reliable across the lifecycle hop and the dialog would fail to open.
        pendingAddRepo.collect { value ->
            value?.let {
                addRepoPrefill = it
                onAddRepoHandled()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showFilters = true }) {
                if (filtersActive) {
                    BadgedBox(badge = { Badge() }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                } else {
                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                }
            }
        },
        topBar = {
            if (searchActive) {
                TopAppBar(
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = exitSearch, tooltip = "Close search") {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    title = {
                        AppSearchField(
                            value = nameFilter,
                            onValueChange = viewModel::setNameFilter,
                            placeholder = "Search extensions…",
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Extensions") },
                    actions = {
                        PlainTooltipIconButton(onClick = { searchActive = true }, tooltip = "Search") {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (ui.updateCount > 0) {
                            PlainTooltipIconButton(onClick = viewModel::updateAll, tooltip = "Update all") {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Update all")
                            }
                        }
                        PlainTooltipIconButton(onClick = { showRepos = true }, tooltip = "Repositories") {
                            Icon(Icons.Default.Storage, contentDescription = "Repositories")
                        }
                    },
                )
            }
        },
    ) { padding ->
        val list = when (section) {
            ExtensionSection.ALL -> ui.installed + ui.available
            ExtensionSection.INSTALLED -> ui.installed
            ExtensionSection.AVAILABLE -> ui.available
            ExtensionSection.UPDATES -> ui.updates
        }
        PullToRefreshBox(
            isRefreshing = isFetching,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (list.isEmpty() && authRequiredRepos.isEmpty()) {
                // A single full-size item keeps the empty state centered while
                // staying scrollable, so pull-to-refresh still works.
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                emptyExtensionsMessage(section),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    if (authRequiredRepos.isNotEmpty()) {
                        item(key = "__auth__") {
                            AuthRequiredBanner(
                                repoNames = authRequiredRepos.map { it.name },
                                signedInAs = githubLogin,
                                onConnect = onConnectGitHub,
                            )
                        }
                    }

                    val extensionRow: @Composable (ExtensionItem) -> Unit = { item ->
                        ExtensionRow(
                            item = item,
                            state = installStates[item.packageName],
                            onInstall = viewModel::install,
                            onUpdate = viewModel::update,
                            onDismissError = viewModel::dismissInstallError,
                            onSettings = onOpenSourceSettings,
                            onRemove = { pendingRemove = it },
                        )
                        HorizontalDivider()
                    }

                    if (section == ExtensionSection.ALL) {
                        // "All" splits into labelled Installed / Available groups.
                        if (ui.installed.isNotEmpty()) {
                            item(key = "__installed_header__") { ExtensionSectionHeader("Installed") }
                            items(ui.installed, key = { it.packageName }) { extensionRow(it) }
                        }
                        if (ui.available.isNotEmpty()) {
                            item(key = "__available_header__") { ExtensionSectionHeader("Available") }
                            items(ui.available, key = { it.packageName }) { extensionRow(it) }
                        }
                    } else {
                        items(list, key = { it.packageName }) { extensionRow(it) }
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = filterSheetState,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FilterSheetLabel("Show")
                SectionFilterChips(
                    section = section,
                    updateCount = ui.updateCount,
                    onSelect = viewModel::setSection,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (ui.languages.size > 1) {
                    FilterSheetLabel("Language")
                    LanguageFilterChips(
                        languages = ui.languages,
                        selected = languageFilter,
                        onSelect = viewModel::setLanguageFilter,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
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
                PlainTooltipIconButton(onClick = { showAddRepo = true }, tooltip = "Add repository") {
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
                                        PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = "Options") {
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
            initialName = "",
            initialUrl = "",
            isEdit = false,
            onConfirm = { name, url -> viewModel.addRepo(name, url); showAddRepo = false },
            onDismiss = { showAddRepo = false },
        )
    }

    addRepoPrefill?.let { prefill ->
        RepoDialog(
            initialName = prefill.name.orEmpty(),
            initialUrl = prefill.url,
            isEdit = false,
            onConfirm = { name, url -> viewModel.addRepo(name, url); addRepoPrefill = null },
            onDismiss = { addRepoPrefill = null },
        )
    }

    editRepo?.let { repo ->
        RepoDialog(
            initialName = repo.name,
            initialUrl = repo.indexUrl,
            isEdit = true,
            onConfirm = { name, url -> viewModel.updateRepo(repo, name, url); editRepo = null },
            onDismiss = { editRepo = null },
        )
    }

    if (rateLimitPrompt) {
        val notConnected = githubLogin == null
        AlertDialog(
            onDismissRequest = viewModel::dismissRateLimitPrompt,
            title = { Text("GitHub rate limit reached") },
            text = {
                Text(
                    if (notConnected) {
                        "You've hit GitHub's anonymous request limit (60 per hour). " +
                            "Connect a GitHub account to raise it to 5,000 per hour, or try again later."
                    } else {
                        "You've hit GitHub's request limit. Please wait a little while and try again."
                    }
                )
            },
            confirmButton = {
                if (notConnected) {
                    TextButton(onClick = {
                        viewModel.dismissRateLimitPrompt()
                        onConnectGitHub()
                    }) { Text("Connect GitHub") }
                } else {
                    TextButton(onClick = viewModel::dismissRateLimitPrompt) { Text("OK") }
                }
            },
            dismissButton = if (notConnected) {
                { TextButton(onClick = viewModel::dismissRateLimitPrompt) { Text("Later") } }
            } else {
                null
            },
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
private fun ExtensionSectionHeader(text: String) {
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
private fun FilterSheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SectionFilterChips(
    section: ExtensionSection,
    updateCount: Int,
    onSelect: (ExtensionSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = section == ExtensionSection.ALL,
            onClick = { onSelect(ExtensionSection.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = section == ExtensionSection.INSTALLED,
            onClick = { onSelect(ExtensionSection.INSTALLED) },
            label = { Text("Installed") },
        )
        FilterChip(
            selected = section == ExtensionSection.AVAILABLE,
            onClick = { onSelect(ExtensionSection.AVAILABLE) },
            label = { Text("Available") },
        )
        FilterChip(
            selected = section == ExtensionSection.UPDATES,
            onClick = { onSelect(ExtensionSection.UPDATES) },
            label = { Text(if (updateCount > 0) "Updates ($updateCount)" else "Updates") },
        )
    }
}

@Composable
private fun ExtensionRow(
    item: ExtensionItem,
    state: InstallState?,
    onInstall: (ExtensionItem.Available) -> Unit,
    onUpdate: (ExtensionItem.Installed) -> Unit,
    onDismissError: (String) -> Unit,
    onSettings: (String) -> Unit,
    onRemove: (ExtensionItem) -> Unit,
) {
    val isInstalled = item is ExtensionItem.Installed || item is ExtensionItem.InstalledOnly
    val hasUpdate = item is ExtensionItem.Installed && item.hasUpdate
    val hasSettings = item.hasSettings()
    val languages = item.multiLanguageOptions()
    SourceListItem(
        name = item.name,
        lang = item.lang,
        packageName = item.packageName,
        iconUrl = item.iconUrl,
        onClick = {
            if (isInstalled) { if (hasSettings) onSettings(item.packageName) }
            else if (item is ExtensionItem.Available) onInstall(item)
        },
        supporting = {
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
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isInstalled) {
                    when {
                        state is InstallState.Downloading -> Text(
                            "${downloadPercent(state)}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        state is InstallState.Error -> OutlinedButton(onClick = {
                            if (item is ExtensionItem.Installed) onUpdate(item)
                            else onDismissError(item.packageName)
                        }) { Text("Retry") }
                        hasUpdate -> OutlinedButton(onClick = {
                            onUpdate(item as ExtensionItem.Installed)
                        }) { Text("Update") }
                    }
                    if (hasSettings) {
                        PlainTooltipIconButton(onClick = { onSettings(item.packageName) }, tooltip = "Settings") {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    PlainTooltipIconButton(onClick = { onRemove(item) }, tooltip = "Remove") {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                } else {
                    when (state) {
                        is InstallState.Downloading -> Text(
                            "${downloadPercent(state)}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        is InstallState.Error -> OutlinedButton(onClick = {
                            (item as? ExtensionItem.Available)?.let(onInstall)
                        }) { Text("Retry") }
                        null -> PlainTooltipIconButton(onClick = {
                            (item as? ExtensionItem.Available)?.let(onInstall)
                        }, tooltip = "Install") { Icon(Icons.Default.Download, contentDescription = "Install") }
                    }
                }
            }
        },
    )
}

private fun emptyExtensionsMessage(section: ExtensionSection): String = when (section) {
    ExtensionSection.ALL -> "No extensions found\nAdd a repository to discover extensions"
    ExtensionSection.INSTALLED -> "No installed extensions\nAdd a repository to discover extensions"
    ExtensionSection.AVAILABLE -> "No available extensions\nAdd a repository to discover extensions"
    ExtensionSection.UPDATES -> "Everything's up to date"
}

@Composable
private fun RepoDialog(
    initialName: String,
    initialUrl: String,
    isEdit: Boolean,
    onConfirm: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    val urlError = url.isNotEmpty() && (!url.startsWith("http") || !url.endsWith(".json"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Repository" else "Add Repository") },
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

private fun ExtensionItem.multiLanguageOptions(): List<String>? = when (this) {
    is ExtensionItem.Installed -> (loaded.source as? MultiLanguageSource)?.availableLanguages()
    is ExtensionItem.InstalledOnly -> (loaded.source as? MultiLanguageSource)?.availableLanguages()
    is ExtensionItem.Available -> null
}

/**
 * Whether opening source settings would show anything: a configurable source, a
 * multi-language picker, or a WebView login. Mirrors the source-settings screen
 * so we don't surface a gear that leads to an empty page.
 */
private fun ExtensionItem.hasSettings(): Boolean {
    val source = when (this) {
        is ExtensionItem.Installed -> loaded.source
        is ExtensionItem.InstalledOnly -> loaded.source
        is ExtensionItem.Available -> return false
    }
    return source is ConfigurableSource ||
        source is MultiLanguageSource ||
        source is WebViewLoginSource ||
        source is MultiHostSource
}

private const val MULTI_LANGUAGE_PREVIEW_COUNT = 3

private fun multiLanguageSummary(languages: List<String>): String {
    val shown = languages.take(MULTI_LANGUAGE_PREVIEW_COUNT)
    val extra = languages.size - shown.size
    return shown.joinToString(", ") + if (extra > 0) " + $extra more" else ""
}
