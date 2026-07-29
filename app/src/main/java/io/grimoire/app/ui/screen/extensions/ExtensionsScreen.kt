package io.grimoire.app.ui.screen.extensions

import io.grimoire.app.ui.icon.*
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.grimoire.app.ui.tour.TourKey
import io.grimoire.app.ui.tour.tourTarget
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.source.feature.ConfigurableSource
import io.grimoire.api.source.feature.MultiHostSource
import io.grimoire.api.source.feature.MultiLanguageSource
import io.grimoire.api.source.feature.WebViewLoginSource
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.ui.PendingAddRepo
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.SearchCancelToolbar
import io.grimoire.app.ui.component.LanguageMultiSelectChips
import io.grimoire.app.ui.component.LinkText
import io.grimoire.app.ui.component.SourceListItem
import io.grimoire.app.util.ContentLanguages
import io.grimoire.app.util.languageLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.grimoire.app.R

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
    prefillQuery: String = "",
) {
    val ui by viewModel.ui.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val installStates by viewModel.installStates.collectAsState()
    val authRequiredRepos by viewModel.authRequiredRepos.collectAsState()
    val githubLogin by viewModel.githubLogin.collectAsState()
    val rateLimitPrompt by viewModel.rateLimitPrompt.collectAsState()
    val nameFilter by viewModel.nameFilter.collectAsState()
    val enabledLanguages by viewModel.enabledLanguages.collectAsState()
    val adultFilter by viewModel.adultFilter.collectAsState()
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
    val filtersActive = section != ExtensionSection.ALL || enabledLanguages.isNotEmpty() ||
        adultFilter != AdultFilter.ALL

    val exitSearch = {
        searchActive = false
        viewModel.setNameFilter("")
    }
    BackHandler(enabled = searchActive) { exitSearch() }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }
    // Deep-linked from a novel whose source is uninstalled: pre-fill the search so
    // that source is easy to find and install.
    LaunchedEffect(prefillQuery) {
        if (prefillQuery.isNotBlank()) {
            viewModel.setNameFilter(prefillQuery)
            searchActive = true
        }
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
        topBar = {
            Box {
                TopAppBar(
                    navigationIcon = {
                        PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                            Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    title = { Text(stringResource(R.string.extensions_title)) },
                    actions = {
                        // Repos + Search + Filter live in the floating bottom toolbar;
                        // Update-all stays here since it's contextual.
                        if (ui.updateCount > 0) {
                            PlainTooltipIconButton(onClick = viewModel::updateAll, tooltip = stringResource(R.string.extensions_update_all)) {
                                Icon(AppIcons.SystemUpdateAlt, contentDescription = stringResource(R.string.extensions_update_all))
                            }
                        }
                    },
                )
                // A search toolbar slides in over the main one when Search is tapped.
                AnimatedVisibility(
                    visible = searchActive,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        title = {
                            AppSearchField(
                                value = nameFilter,
                                onValueChange = viewModel::setNameFilter,
                                placeholder = stringResource(R.string.extensions_search_placeholder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                            )
                        },
                    )
                }
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
                        // "All" surfaces pending updates at the top, then the
                        // labelled Installed (sans-updates) / Available groups.
                        if (ui.updates.isNotEmpty()) {
                            item(key = "__updates_header__") {
                                ExtensionSectionHeader(stringResource(R.string.extensions_updates_count, ui.updates.size))
                            }
                            items(ui.updates, key = { it.packageName }) { extensionRow(it) }
                        }
                        val installedNoUpdates = ui.installed
                            .filterNot { it is ExtensionItem.Installed && it.hasUpdate }
                        if (installedNoUpdates.isNotEmpty()) {
                            item(key = "__installed_header__") { ExtensionSectionHeader(stringResource(R.string.extensions_installed)) }
                            items(installedNoUpdates, key = { it.packageName }) { extensionRow(it) }
                        }
                        if (ui.available.isNotEmpty()) {
                            item(key = "__available_header__") { ExtensionSectionHeader(stringResource(R.string.extensions_available)) }
                            items(ui.available, key = { it.packageName }) { extensionRow(it) }
                        }
                    } else {
                        items(list, key = { it.packageName }) { extensionRow(it) }
                    }
                }
            }

            // Floating pill mirroring Browse: Repos + Filter + Search anchored bottom-centre.
            // While searching it swaps to a single X that cancels the search.
            val pillModifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
            if (searchActive) {
                SearchCancelToolbar(
                    onCancel = exitSearch,
                    contentDescription = stringResource(R.string.extensions_close_search),
                    modifier = pillModifier,
                )
            } else {
                ExtensionsBottomToolbar(
                    onRepos = { showRepos = true },
                    onFilter = { showFilters = true },
                    onSearch = { searchActive = true },
                    filterActive = filtersActive,
                    modifier = pillModifier,
                )
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
                    stringResource(R.string.extensions_filters),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FilterSheetLabel(stringResource(R.string.extensions_show))
                SectionFilterChips(
                    section = section,
                    updateCount = ui.updateCount,
                    onSelect = viewModel::setSection,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (ui.languages.size > 1) {
                    FilterSheetLabel(stringResource(R.string.extensions_language))
                    LanguageMultiSelectChips(
                        languages = ui.languages,
                        enabled = enabledLanguages.mapTo(mutableSetOf()) { it.code },
                        onToggle = { viewModel.toggleLanguage(it, ui.languages) },
                        onAll = viewModel::clearLanguageFilter,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                FilterSheetLabel(stringResource(R.string.extensions_adult_content))
                AdultFilterChips(
                    selected = adultFilter,
                    onSelect = viewModel::setAdultFilter,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
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
                    stringResource(R.string.extensions_repositories),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PlainTooltipIconButton(onClick = { showAddRepo = true }, tooltip = stringResource(R.string.extensions_add_repository)) {
                    Icon(AppIcons.Add, contentDescription = stringResource(R.string.extensions_add_repository))
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
                        stringResource(R.string.extensions_no_repositories),
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
                                        PlainTooltipIconButton(onClick = { menuExpanded = true }, tooltip = stringResource(R.string.extensions_options)) {
                                            Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.extensions_options))
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_edit)) },
                                                leadingIcon = { Icon(AppIcons.Edit, null) },
                                                onClick = { menuExpanded = false; editRepo = repo },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_delete)) },
                                                leadingIcon = { Icon(AppIcons.Delete, null) },
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
            title = { Text(stringResource(R.string.extensions_rate_limit_title)) },
            text = {
                Text(
                    if (notConnected) {
                        stringResource(R.string.extensions_rate_limit_anonymous)
                    } else {
                        stringResource(R.string.extensions_rate_limit_connected)
                    }
                )
            },
            confirmButton = {
                if (notConnected) {
                    TextButton(onClick = {
                        viewModel.dismissRateLimitPrompt()
                        onConnectGitHub()
                    }) { Text(stringResource(R.string.extensions_connect_github)) }
                } else {
                    TextButton(onClick = viewModel::dismissRateLimitPrompt) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismissButton = if (notConnected) {
                { TextButton(onClick = viewModel::dismissRateLimitPrompt) { Text(stringResource(R.string.action_later)) } }
            } else {
                null
            },
        )
    }

    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.extensions_remove_title)) },
            text = { Text(stringResource(R.string.extensions_remove_description, item.name)) },
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
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text(stringResource(R.string.action_cancel)) }
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
            label = { Text(stringResource(R.string.extensions_all)) },
        )
        FilterChip(
            selected = section == ExtensionSection.INSTALLED,
            onClick = { onSelect(ExtensionSection.INSTALLED) },
            label = { Text(stringResource(R.string.extensions_installed)) },
        )
        FilterChip(
            selected = section == ExtensionSection.AVAILABLE,
            onClick = { onSelect(ExtensionSection.AVAILABLE) },
            label = { Text(stringResource(R.string.extensions_available)) },
        )
        FilterChip(
            selected = section == ExtensionSection.UPDATES,
            onClick = { onSelect(ExtensionSection.UPDATES) },
            label = {
                Text(
                    if (updateCount > 0) stringResource(R.string.extensions_updates_count, updateCount)
                    else stringResource(R.string.extensions_updates),
                )
            },
        )
    }
}

@Composable
private fun AdultFilterChips(
    selected: AdultFilter,
    onSelect: (AdultFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == AdultFilter.ALL,
            onClick = { onSelect(AdultFilter.ALL) },
            label = { Text(stringResource(R.string.extensions_all)) },
        )
        FilterChip(
            selected = selected == AdultFilter.HIDE,
            onClick = { onSelect(AdultFilter.HIDE) },
            label = { Text(stringResource(R.string.extensions_hide_adult)) },
        )
        FilterChip(
            selected = selected == AdultFilter.ONLY,
            onClick = { onSelect(AdultFilter.ONLY) },
            label = { Text(stringResource(R.string.extensions_only_adult)) },
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
    // availableLanguages() is suspend (a source may scrape its language menu).
    val languages by produceState<List<String>?>(null, item) { value = item.multiLanguageOptions() }
    SourceListItem(
        name = item.name,
        lang = item.lang,
        packageName = item.packageName,
        iconUrl = item.iconUrl,
        adult = item.isAdult,
        onClick = {
            if (isInstalled) { if (hasSettings) onSettings(item.packageName) }
            else if (item is ExtensionItem.Available) onInstall(item)
        },
        supporting = {
            Column {
                Text("${languageLabel(item.lang)} · v${item.versionName}")
                val langs = languages
                if (!langs.isNullOrEmpty()) {
                    Text(
                        multiLanguageSummary(langs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasUpdate && state == null) {
                    Text(
                        stringResource(
                            R.string.extensions_update_available,
                            (item as ExtensionItem.Installed).remoteVersionName,
                        ),
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
                        }) { Text(stringResource(R.string.action_retry)) }
                        hasUpdate -> OutlinedButton(onClick = {
                            onUpdate(item as ExtensionItem.Installed)
                        }) { Text(stringResource(R.string.extensions_update)) }
                    }
                    if (hasSettings) {
                        PlainTooltipIconButton(onClick = { onSettings(item.packageName) }, tooltip = stringResource(R.string.action_settings)) {
                            Icon(AppIcons.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    }
                    PlainTooltipIconButton(onClick = { onRemove(item) }, tooltip = stringResource(R.string.action_remove)) {
                        Icon(AppIcons.Delete, contentDescription = stringResource(R.string.action_remove))
                    }
                } else {
                    when (state) {
                        is InstallState.Downloading -> Text(
                            "${downloadPercent(state)}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        is InstallState.Error -> OutlinedButton(onClick = {
                            (item as? ExtensionItem.Available)?.let(onInstall)
                        }) { Text(stringResource(R.string.action_retry)) }
                        null -> PlainTooltipIconButton(onClick = {
                            (item as? ExtensionItem.Available)?.let(onInstall)
                        }, tooltip = stringResource(R.string.action_install)) {
                            Icon(AppIcons.Download, contentDescription = stringResource(R.string.action_install))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun emptyExtensionsMessage(section: ExtensionSection): String = stringResource(
    when (section) {
        ExtensionSection.ALL -> R.string.extensions_empty_all
        ExtensionSection.INSTALLED -> R.string.extensions_empty_installed
        ExtensionSection.AVAILABLE -> R.string.extensions_empty_available
        ExtensionSection.UPDATES -> R.string.extensions_empty_updates
    },
)

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
        title = {
            Text(stringResource(if (isEdit) R.string.extensions_edit_repository else R.string.extensions_add_repository_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.extensions_repository_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.extensions_repository_index_url)) },
                    placeholder = { Text("https://example.com/index.json") },
                    singleLine = true,
                    isError = urlError,
                    supportingText = if (urlError) ({ Text(stringResource(R.string.extensions_repository_url_error)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank() && !urlError,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
                    stringResource(
                        if (repoNames.size == 1) R.string.extensions_sign_in_required_one
                        else R.string.extensions_sign_in_required_many,
                        if (repoNames.size == 1) repoNames.first() else joinedNames,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.extensions_private_repositories),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onConnect) { Text(stringResource(R.string.extensions_connect_github)) }
            } else {
                Text(
                    stringResource(
                        if (repoNames.size == 1) R.string.extensions_no_access_one
                        else R.string.extensions_no_access_many,
                        if (repoNames.size == 1) repoNames.first() else joinedNames,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                LinkText(
                    text = stringResource(R.string.extensions_org_access, signedInAs),
                    "github.com/settings/applications" to "https://github.com/settings/applications",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onConnect) { Text(stringResource(R.string.extensions_manage_github)) }
            }
        }
    }
}

private suspend fun ExtensionItem.multiLanguageOptions(): List<String>? {
    val source = when (this) {
        is ExtensionItem.Installed -> loaded.source
        is ExtensionItem.InstalledOnly -> loaded.source
        is ExtensionItem.Available -> null
    }
    return (source as? MultiLanguageSource)?.availableLanguages()
        ?.let { ContentLanguages.displayNames(it) }
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

@Composable
private fun multiLanguageSummary(languages: List<String>): String {
    val shown = languages.take(MULTI_LANGUAGE_PREVIEW_COUNT)
    val extra = languages.size - shown.size
    val summary = shown.joinToString(", ")
    return if (extra > 0) stringResource(R.string.extensions_more_languages, summary, extra) else summary
}

/**
 * Floating toolbar pill anchored bottom-centre on Extensions, mirroring Browse:
 * Repositories, Filter (tinted when a filter is active), and Search.
 */
@Composable
private fun ExtensionsBottomToolbar(
    onRepos: () -> Unit,
    onFilter: () -> Unit,
    onSearch: () -> Unit,
    filterActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onRepos) {
                Icon(
                    AppIcons.Storage,
                    contentDescription = stringResource(R.string.extensions_repositories),
                    modifier = Modifier.tourTarget(TourKey.RepoManager),
                )
            }
            IconButton(onClick = onFilter) {
                Icon(
                    AppIcons.FilterList,
                    contentDescription = stringResource(R.string.extensions_filters),
                    tint = if (filterActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    AppIcons.Search,
                    contentDescription = stringResource(R.string.action_search),
                )
            }
        }
    }
}
