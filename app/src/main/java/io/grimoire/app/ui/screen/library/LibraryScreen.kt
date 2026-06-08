package io.grimoire.app.ui.screen.library

import android.net.Uri
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.ALL_TAB_CATEGORY_ID
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.MoveToCategorySheet
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle
import io.grimoire.app.ui.component.TooltipIconButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val EPUB_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/zip",
    "application/octet-stream",
)

private val EmptyExternalEpubUri: StateFlow<Uri?> = MutableStateFlow(null).asStateFlow()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNovelClick: (pkg: String, url: String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectionActiveChange: (Boolean) -> Unit = {},
    pendingEpubUri: StateFlow<Uri?> = EmptyExternalEpubUri,
    onEpubUriHandled: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val chapterStats by viewModel.chapterStats.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val filterStatuses by viewModel.filterStatuses.collectAsState()
    val filterUnreadOnly by viewModel.filterUnreadOnly.collectAsState()
    val filterDownloadedOnly by viewModel.filterDownloadedOnly.collectAsState()
    val filterNotifyEnabled by viewModel.filterNotifyEnabled.collectAsState()
    val filterAutoDownloadEnabled by viewModel.filterAutoDownloadEnabled.collectAsState()
    val filterSourceIds by viewModel.filterSourceIds.collectAsState()
    val librarySources by viewModel.librarySources.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val includeLockedInTotals by viewModel.includeLockedInTotals.collectAsState()
    val showReadBadge by viewModel.showReadBadge.collectAsState()
    val showDownloadedBadge by viewModel.showDownloadedBadge.collectAsState()
    val showLockedBadge by viewModel.showLockedBadge.collectAsState()
    val staging by viewModel.staging.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val persistedCategoryId by viewModel.persistedCategoryId.collectAsState()
    val categoriesLoaded by viewModel.categoriesLoaded.collectAsState()
    val displayedTabs by viewModel.displayedTabs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showManage by remember { mutableStateOf(false) }
    var libraryMenuExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showRefreshSheet by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val filterSheetState = rememberModalBottomSheetState()

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkMove by remember { mutableStateOf(false) }
    var showBulkRemoveConfirm by remember { mutableStateOf(false) }
    val toggleSelect: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    val clearSelection = { selectedIds = emptySet() }

    BackHandler(enabled = selectionMode) { clearSelection() }

    // Let the app nav hide while selecting so the selection bar can replace it.
    LaunchedEffect(selectionMode) { onSelectionActiveChange(selectionMode) }

    val epubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.stageEpub(uri)
    }

    val externalEpubUri by pendingEpubUri.collectAsState()
    LaunchedEffect(externalEpubUri) {
        val uri = externalEpubUri ?: return@LaunchedEffect
        viewModel.stageEpub(uri)
        onEpubUriHandled()
    }

    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeImportMessage()
        }
    }

    val isFilterActive = filterStatuses.isNotEmpty() || filterUnreadOnly || filterDownloadedOnly ||
        filterNotifyEnabled || filterAutoDownloadEnabled || filterSourceIds.isNotEmpty()

    val tabs = displayedTabs.map { it.label }
    // Parallel to `tabs`: the category id behind each tab, or ALL_TAB_CATEGORY_ID for "All".
    val tabCategoryIds = displayedTabs.map { it.categoryId }

    val pageCount = tabs.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = tabCategoryIds.indexOf(persistedCategoryId ?: ALL_TAB_CATEGORY_ID)
            .coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )
    val currentTab = pagerState.currentPage.coerceIn(0, pageCount - 1)

    // Restore the last-viewed category once the persisted id, the category list, AND the
    // built tabs are all available. `resolveRestoreTargetPage` returns null until then so
    // the restore can't fire during the startup window where the tabs are still empty and
    // latch onto the fallback tab, losing the saved category. A remembered category that is
    // now hidden (the app starts locked) is absent from `tabCategoryIds`, so it resolves to
    // the first tab and stays hidden.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(restored, categoriesLoaded, persistedCategoryId, tabCategoryIds) {
        if (restored) return@LaunchedEffect
        val target = resolveRestoreTargetPage(categoriesLoaded, persistedCategoryId, tabCategoryIds)
            ?: return@LaunchedEffect
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        restored = true
    }

    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage(pageCount - 1)
        }
    }

    // Persist the category the user settles on. `drop(1)` skips the settle emitted by
    // the restore above, so a remembered-but-hidden category isn't overwritten with the
    // fallback tab and is restored again on a later unlocked reopen.
    val latestTabCategoryIds by rememberUpdatedState(tabCategoryIds)
    LaunchedEffect(pagerState, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                viewModel.setSelectedCategoryId(
                    latestTabCategoryIds.getOrNull(page) ?: ALL_TAB_CATEGORY_ID
                )
            }
    }

    val displayedNovels: List<NovelEntity>? = displayedTabs.getOrNull(currentTab)?.novels

    Scaffold(
        modifier = modifier,
        // The parent AppNavigation Scaffold already handles system bar insets
        // (its NavigationBar / our selection bar cover the bottom area), so
        // don't double-apply them here - that's what caused the dark gap
        // between the last row and the app nav.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        // Toggle just the current tab's novels within the
                        // (possibly cross-category) selection.
                        val ids = displayedNovels?.map { it.id }?.toSet().orEmpty()
                        selectedIds = if (ids.isNotEmpty() && selectedIds.containsAll(ids)) {
                            selectedIds - ids
                        } else {
                            selectedIds + ids
                        }
                    },
                )
            } else {
            TopAppBar(
                title = {
                    if (searchActive) {
                        AppSearchField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = "Search library…",
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    } else {
                        Text(
                            "Library",
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { if (hasPin && !isUnlocked) showUnlock = true },
                            ),
                        )
                    }
                },
                actions = {
                    PlainTooltipIconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            viewModel.setSearchQuery("")
                            keyboard?.hide()
                        } else {
                            searchActive = true
                        }
                    }, tooltip = if (searchActive) "Close search" else "Search library") {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchActive) "Close search" else "Search library",
                        )
                    }
                    if (!searchActive) {
                        if (isUnlocked) {
                            PlainTooltipIconButton(onClick = { viewModel.lock() }, tooltip = "Lock hidden categories") {
                                Icon(Icons.Default.Lock, contentDescription = "Lock hidden categories")
                            }
                        }
                        PlainTooltipIconButton(onClick = { showFilterSheet = true }, tooltip = "Filter & sort") {
                            BadgedBox(badge = { if (isFilterActive) Badge() }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter & sort")
                            }
                        }
                        PlainTooltipIconButton(onClick = { showRefreshSheet = true }, tooltip = "Refresh library") {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh library")
                        }
                        Box {
                            PlainTooltipIconButton(onClick = { libraryMenuExpanded = true }, tooltip = "More actions") {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = libraryMenuExpanded,
                                onDismissRequest = { libraryMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (importing || staging) "Importing EPUB…" else "Import EPUB")
                                    },
                                    enabled = !importing && !staging,
                                    onClick = {
                                        libraryMenuExpanded = false
                                        epubPicker.launch(EPUB_MIME_TYPES)
                                    },
                                    leadingIcon = {
                                        if (importing || staging) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(Icons.Default.LibraryAdd, null)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage categories") },
                                    onClick = {
                                        libraryMenuExpanded = false
                                        showManage = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                )
                            }
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            // Library keeps its no-fade slide so the bar feels glued to the app nav.
            TooltipBottomBar(
                visible = selectionMode,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                TooltipIconButton(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    label = "Move",
                    onClick = { showBulkMove = true },
                )
                TooltipIconButton(
                    icon = Icons.Default.DoneAll,
                    label = "Mark read",
                    onClick = {
                        viewModel.setNovelsRead(selectedIds, true)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = Icons.Default.RemoveDone,
                    label = "Mark unread",
                    onClick = {
                        viewModel.setNovelsRead(selectedIds, false)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = Icons.Default.Download,
                    label = "Download",
                    onClick = {
                        val count = selectedIds.size
                        viewModel.downloadNovels(selectedIds)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Queued downloads for $count novels"
                            )
                        }
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = Icons.Default.Delete,
                    label = "Remove",
                    onClick = { showBulkRemoveConfirm = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) { padding ->
            val onNovelClickWrapped: (NovelEntity) -> Unit = { novel ->
                val pkg = viewModel.pkgForNovel(novel)
                if (pkg.isNotEmpty()) onNovelClick(pkg, novel.url)
                else scope.launch { snackbarHostState.showSnackbar("Extension not installed") }
            }

            SwipeTabRow(
                tabs = tabs,
                modifier = Modifier.padding(padding),
                pagerState = pagerState,
                style = SwipeTabStyle.PrimaryScrollable,
                // A lone "All" category shows no tab strip, just its page.
                hideTabRowForSingleTab = true,
            ) { page ->
                val pageNovels = displayedTabs.getOrNull(page)?.novels

                when {
                    pageNovels == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    pageNovels.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (searchQuery.isNotBlank()) {
                            Text(
                                "No matches found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.LibraryAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Your library is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Add novels from a source, or import an EPUB from your device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onBrowse) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Browse sources")
                                    }
                                    TextButton(
                                        onClick = { epubPicker.launch(EPUB_MIME_TYPES) },
                                        enabled = !importing && !staging,
                                    ) {
                                        Icon(
                                            Icons.Default.LibraryAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Import EPUB")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (displayMode == LibraryDisplayMode.GRID) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(pageNovels, key = { it.id }) { novel ->
                                    NovelCard(
                                        novel = novel,
                                        stats = chapterStats[novel.id],
                                        includeLockedInTotals = includeLockedInTotals,
                                        showReadBadge = showReadBadge,
                                        showDownloadedBadge = showDownloadedBadge,
                                        showLockedBadge = showLockedBadge,
                                        selected = novel.id in selectedIds,
                                        onClick = {
                                            if (selectionMode) toggleSelect(novel.id)
                                            else onNovelClickWrapped(novel)
                                        },
                                        onLongClick = { toggleSelect(novel.id) },
                                    )
                                }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(pageNovels, key = { it.id }) { novel ->
                                    NovelRow(
                                        novel = novel,
                                        stats = chapterStats[novel.id],
                                        includeLockedInTotals = includeLockedInTotals,
                                        showReadBadge = showReadBadge,
                                        showDownloadedBadge = showDownloadedBadge,
                                        showLockedBadge = showLockedBadge,
                                        selected = novel.id in selectedIds,
                                        onClick = {
                                            if (selectionMode) toggleSelect(novel.id)
                                            else onNovelClickWrapped(novel)
                                        },
                                        onLongClick = { toggleSelect(novel.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
        ) {
            FilterSortContent(
                sortField = sortField,
                sortDirection = sortDirection,
                filterStatuses = filterStatuses,
                filterUnreadOnly = filterUnreadOnly,
                filterDownloadedOnly = filterDownloadedOnly,
                filterNotifyEnabled = filterNotifyEnabled,
                filterAutoDownloadEnabled = filterAutoDownloadEnabled,
                filterSourceIds = filterSourceIds,
                librarySources = librarySources,
                onSortFieldChange = viewModel::setSortField,
                onToggleSortDirection = viewModel::toggleSortDirection,
                onToggleFilterStatus = viewModel::toggleFilterStatus,
                onUnreadOnlyChange = viewModel::setFilterUnreadOnly,
                onDownloadedOnlyChange = viewModel::setFilterDownloadedOnly,
                onNotifyEnabledChange = viewModel::setFilterNotifyEnabled,
                onAutoDownloadEnabledChange = viewModel::setFilterAutoDownloadEnabled,
                onToggleFilterSource = viewModel::toggleFilterSource,
            )
        }
    }

    if (showRefreshSheet) {
        val refreshSheetState = rememberModalBottomSheetState()
        val currentCategoryId = tabCategoryIds.getOrNull(currentTab) ?: ALL_TAB_CATEGORY_ID
        val currentCategoryName = tabs.getOrElse(currentTab) { "" }
        ModalBottomSheet(
            onDismissRequest = { showRefreshSheet = false },
            sheetState = refreshSheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    "Refresh library",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                ListItem(
                    headlineContent = { Text("Update library") },
                    supportingContent = { Text("Fetch new chapters across all categories") },
                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.updateLibrary()
                        scope.launch { snackbarHostState.showSnackbar("Library update queued") }
                        showRefreshSheet = false
                    },
                )
                if (currentCategoryId != ALL_TAB_CATEGORY_ID) {
                    ListItem(
                        headlineContent = { Text("Update \"$currentCategoryName\"") },
                        supportingContent = {
                            Text("Fetch new chapters for novels in this category")
                        },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.updateCategory(currentCategoryId)
                            scope.launch {
                                snackbarHostState.showSnackbar("Category update queued")
                            }
                            showRefreshSheet = false
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("Cancel") },
                    leadingContent = {
                        Icon(Icons.Default.Close, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showRefreshSheet = false },
                )
            }
        }
    }

    if (showManage) {
        ModalBottomSheet(
            onDismissRequest = { showManage = false },
            sheetState = sheetState,
        ) {
            ManageCategoriesSheet(
                categories = categories,
                isUnlocked = isUnlocked,
                hasPin = hasPin,
                onAdd = viewModel::addCategory,
                onRename = { cat, name -> viewModel.renameCategory(cat, name) },
                onDelete = viewModel::deleteCategory,
                onToggleHidden = { cat, hidden -> viewModel.setCategoryHidden(cat, hidden) },
                onMove = { from, to -> viewModel.moveCategory(categories, from, to) },
                onUnlockRequest = { showUnlock = true },
            )
        }
    }

    if (showUnlock) {
        HiddenCategoriesUnlockDialog(
            biometricEnabled = biometricEnabled,
            onVerifyPin = { pin -> viewModel.verifyAndUnlock(pin) },
            onUnlockedByBiometric = { viewModel.unlockFromBiometric() },
            onDismiss = { showUnlock = false },
        )
    }

    pendingImport?.let { staged ->
        EpubImportPreviewDialog(
            staged = staged,
            importing = importing,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }

    if (showBulkMove) {
        val canUnlockHidden by viewModel.canUnlockHidden.collectAsState()
        MoveToCategorySheet(
            categories = categories,
            count = selectedIds.size,
            onSelect = { catId ->
                viewModel.moveNovels(selectedIds, catId)
                showBulkMove = false
                clearSelection()
            },
            onDismiss = { showBulkMove = false },
            onUnlockClick = if (canUnlockHidden) { { showUnlock = true } } else null,
        )
    }

    if (showBulkRemoveConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkRemoveConfirm = false },
            title = { Text("Remove from library") },
            text = {
                Text(
                    "Remove $count ${if (count == 1) "novel" else "novels"} from your library? " +
                        "Downloaded chapters and read progress are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeNovelsFromLibrary(selectedIds)
                    showBulkRemoveConfirm = false
                    clearSelection()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkRemoveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

