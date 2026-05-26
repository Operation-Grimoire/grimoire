package io.grimoire.app.ui.screen.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.MoveToCategorySheet
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.TooltipIconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.epub.StagedEpub
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.effectiveTotal
import io.grimoire.app.data.local.entity.readPercent
import io.grimoire.app.data.preferences.ALL_TAB_CATEGORY_ID
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.SortDirection
import io.grimoire.app.data.preferences.SortField
import io.grimoire.app.ui.theme.premiumGold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

private val EPUB_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/zip",
    "application/octet-stream",
)

private val EmptyExternalEpubUri: StateFlow<Uri?> = MutableStateFlow(null).asStateFlow()

private val STATUS_OPTIONS = listOf(
    1 to "Ongoing",
    2 to "Completed",
    3 to "Hiatus",
    4 to "Cancelled",
    0 to "Unknown",
)

private val SORT_FIELD_OPTIONS = listOf(
    SortField.LAST_READ to "Last read",
    SortField.TITLE to "Title",
    SortField.LAST_UPDATED to "Last updated",
    SortField.UNREAD to "Unread chapters",
    SortField.TOTAL to "Total chapters",
)

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
        filterSourceIds.isNotEmpty()

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

    // Restore the last-viewed category once the persisted id and the category list are
    // both loaded. A remembered category that is now hidden (the app starts locked) is
    // absent from `tabCategoryIds`, so it falls back to the first tab and stays hidden.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(restored, categoriesLoaded, persistedCategoryId, tabCategoryIds) {
        if (restored || !categoriesLoaded) return@LaunchedEffect
        val savedId = persistedCategoryId ?: return@LaunchedEffect
        val target = tabCategoryIds.indexOf(savedId).takeIf { it >= 0 } ?: 0
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
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            viewModel.setSearchQuery("")
                            keyboard?.hide()
                        } else {
                            searchActive = true
                        }
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchActive) "Close search" else "Search library",
                        )
                    }
                    if (!searchActive) {
                        if (isUnlocked) {
                            IconButton(onClick = { viewModel.lock() }) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock hidden categories")
                            }
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(badge = { if (isFilterActive) Badge() }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter & sort")
                            }
                        }
                        IconButton(onClick = { showRefreshSheet = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh library")
                        }
                        Box {
                            IconButton(onClick = { libraryMenuExpanded = true }) {
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
                    icon = Icons.Default.DriveFileMove,
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (tabs.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = currentTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = currentTab == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) },
                        )
                    }
                }
            }

            val onNovelClickWrapped: (NovelEntity) -> Unit = { novel ->
                val pkg = viewModel.pkgForNovel(novel)
                if (pkg.isNotEmpty()) onNovelClick(pkg, novel.url)
                else scope.launch { snackbarHostState.showSnackbar("Extension not installed") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
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
                filterSourceIds = filterSourceIds,
                librarySources = librarySources,
                onSortFieldChange = viewModel::setSortField,
                onToggleSortDirection = viewModel::toggleSortDirection,
                onToggleFilterStatus = viewModel::toggleFilterStatus,
                onUnreadOnlyChange = viewModel::setFilterUnreadOnly,
                onDownloadedOnlyChange = viewModel::setFilterDownloadedOnly,
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

@Composable
private fun EpubImportPreviewDialog(
    staged: StagedEpub,
    importing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val author = staged.author?.takeIf { it.isNotBlank() }
    val description = staged.description?.takeIf { it.isNotBlank() }
    val coverBytes = staged.coverBytes
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text("Import EPUB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (coverBytes != null) {
                        AsyncImage(
                            model = ByteBuffer.wrap(coverBytes),
                            contentDescription = staged.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(staged.title, style = MaterialTheme.typography.titleMedium)
                        if (author != null) {
                            Text(
                                author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${staged.chapterCount} chapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (staged.genres.isNotEmpty()) {
                            Text(
                                staged.genres.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !importing) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Add to library")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun FilterSortContent(
    sortField: SortField,
    sortDirection: SortDirection,
    filterStatuses: Set<Int>,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    filterSourceIds: Set<Long>,
    librarySources: List<Pair<Long, String>>,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleFilterStatus: (Int?) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    onToggleFilterSource: (Long?) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Filter") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sort") })
        }
        when (tab) {
            0 -> FilterTab(
                filterStatuses = filterStatuses,
                filterUnreadOnly = filterUnreadOnly,
                filterDownloadedOnly = filterDownloadedOnly,
                filterSourceIds = filterSourceIds,
                librarySources = librarySources,
                onToggleFilterStatus = onToggleFilterStatus,
                onUnreadOnlyChange = onUnreadOnlyChange,
                onDownloadedOnlyChange = onDownloadedOnlyChange,
                onToggleFilterSource = onToggleFilterSource,
            )
            1 -> SortTab(
                sortField = sortField,
                sortDirection = sortDirection,
                onSortFieldChange = onSortFieldChange,
                onToggleSortDirection = onToggleSortDirection,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTab(
    filterStatuses: Set<Int>,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    filterSourceIds: Set<Long>,
    librarySources: List<Pair<Long, String>>,
    onToggleFilterStatus: (Int?) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    onToggleFilterSource: (Long?) -> Unit,
) {
    Column {
        Text(
            "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        // FlowRow wraps chips onto multiple lines instead of clipping past the
        // edge — a long status/source list stays visible without horizontal
        // scrolling, which is the multi-select pattern most apps use.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // "All" is rendered first as a distinct chip: tapping it clears the
            // entire selection set rather than toggling a status value. This lets
            // a user always reach the unfiltered state in one tap even when many
            // statuses are selected.
            FilterChip(
                selected = filterStatuses.isEmpty(),
                onClick = { onToggleFilterStatus(null) },
                label = { Text("All") },
            )
            STATUS_OPTIONS.forEach { (ordinal, label) ->
                FilterChip(
                    selected = ordinal in filterStatuses,
                    onClick = { onToggleFilterStatus(ordinal) },
                    label = { Text(label) },
                )
            }
        }
        if (librarySources.size > 1) {
            Text(
                "Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = filterSourceIds.isEmpty(),
                    onClick = { onToggleFilterSource(null) },
                    label = { Text("All") },
                )
                librarySources.forEach { (id, label) ->
                    FilterChip(
                        selected = id in filterSourceIds,
                        onClick = { onToggleFilterSource(id) },
                        label = { Text(label) },
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Unread only") },
            supportingContent = { Text("Hide fully read novels") },
            trailingContent = {
                Switch(checked = filterUnreadOnly, onCheckedChange = onUnreadOnlyChange)
            },
            modifier = Modifier.clickable { onUnreadOnlyChange(!filterUnreadOnly) },
        )
        ListItem(
            headlineContent = { Text("Has downloads") },
            supportingContent = { Text("Show only novels with downloaded chapters") },
            trailingContent = {
                Switch(checked = filterDownloadedOnly, onCheckedChange = onDownloadedOnlyChange)
            },
            modifier = Modifier.clickable { onDownloadedOnlyChange(!filterDownloadedOnly) },
        )
    }
}

@Composable
private fun SortTab(
    sortField: SortField,
    sortDirection: SortDirection,
    onSortFieldChange: (SortField) -> Unit,
    onToggleSortDirection: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SORT_FIELD_OPTIONS.forEach { (field, label) ->
            val selected = sortField == field
            // Tapping the active row flips the direction in place; tapping any other
            // row promotes that field to active without changing the direction. This
            // is the standard Material sort pattern and avoids needing a separate
            // arrow button per row.
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = {
                    if (selected) {
                        Icon(
                            imageVector = if (sortDirection == SortDirection.ASC) {
                                Icons.Default.ArrowUpward
                            } else Icons.Default.ArrowDownward,
                            contentDescription = if (sortDirection == SortDirection.ASC) {
                                "Ascending, tap to flip"
                            } else "Descending, tap to flip",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        // Reserve the leading slot so labels stay aligned across rows.
                        Spacer(Modifier.size(24.dp))
                    }
                },
                modifier = Modifier.clickable {
                    if (selected) onToggleSortDirection() else onSortFieldChange(field)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelCard(
    novel: NovelEntity,
    stats: NovelChapterStats?,
    includeLockedInTotals: Boolean,
    showReadBadge: Boolean,
    showDownloadedBadge: Boolean,
    showLockedBadge: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Column(
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box {
                AsyncImage(
                    model = novel.thumbnailUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (selected) Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                            ) else Modifier
                        ),
                )
                if (selected) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                }
                val visibility = resolveBadgeVisibility(
                    stats = stats,
                    includeLockedInTotals = includeLockedInTotals,
                    showReadBadge = showReadBadge,
                    showDownloadedBadge = showDownloadedBadge,
                    showLockedBadge = showLockedBadge,
                )
                if (visibility.showRead && stats != null) {
                    NovelReadBadgeOverlay(
                        stats = stats,
                        includeLockedInTotals = includeLockedInTotals,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                    )
                }
                if (stats != null && (visibility.showDownloaded || visibility.showLocked)) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (visibility.showDownloaded) {
                            NovelCountBadgeOverlay(
                                count = stats.downloadedCount,
                                icon = Icons.Default.Download,
                                iconContentDescription = null,
                                iconTint = Color.White,
                            )
                        }
                        if (visibility.showLocked) {
                            NovelCountBadgeOverlay(
                                count = stats.lockedCount,
                                icon = Icons.Default.Lock,
                                iconContentDescription = "Locked chapters",
                                iconTint = MaterialTheme.colorScheme.premiumGold,
                            )
                        }
                    }
                }
            }
            Text(
                novel.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelRow(
    novel: NovelEntity,
    stats: NovelChapterStats?,
    includeLockedInTotals: Boolean,
    showReadBadge: Boolean,
    showDownloadedBadge: Boolean,
    showLockedBadge: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else Color.Transparent
        )
    ) {
        ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            headlineContent = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = run {
                val visibility = resolveBadgeVisibility(
                    stats = stats,
                    includeLockedInTotals = includeLockedInTotals,
                    showReadBadge = showReadBadge,
                    showDownloadedBadge = showDownloadedBadge,
                    showLockedBadge = showLockedBadge,
                )
                when {
                    visibility.any && stats != null -> {
                        {
                            NovelStatsRowInline(
                                stats = stats,
                                includeLockedInTotals = includeLockedInTotals,
                                visibility = visibility,
                            )
                        }
                    }
                    !novel.author.isNullOrBlank() -> {
                        { Text(novel.author!!, maxLines = 1) }
                    }
                    else -> null
                }
            },
            leadingContent = {
                Box {
                    AsyncImage(
                        model = novel.thumbnailUrl,
                        contentDescription = novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        )
                    }
                }
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}

@Composable
private fun ManageCategoriesSheet(
    categories: List<CategoryEntity>,
    isUnlocked: Boolean,
    hasPin: Boolean,
    onAdd: (String) -> Unit,
    onRename: (CategoryEntity, String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onToggleHidden: (CategoryEntity, Boolean) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onUnlockRequest: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    Column(Modifier.padding(bottom = 32.dp)) {
        Text(
            "Manage Categories",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        if (hasPin && !isUnlocked) {
            TextButton(
                onClick = onUnlockRequest,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock to manage hidden categories")
            }
        }
        categories.forEachIndexed { index, cat ->
            ListItem(
                headlineContent = { Text(cat.name) },
                leadingContent = {
                    Column {
                        IconButton(
                            onClick = { onMove(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                            )
                        }
                        IconButton(
                            onClick = { onMove(index, index + 1) },
                            enabled = index < categories.lastIndex,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                            )
                        }
                    }
                },
                trailingContent = {
                    Row {
                        if (isUnlocked && !cat.isDefault) {
                            IconButton(onClick = { onToggleHidden(cat, !cat.isHidden) }) {
                                Icon(
                                    if (cat.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (cat.isHidden) "Unhide" else "Hide",
                                )
                            }
                        }
                        IconButton(onClick = { renamingCategory = cat }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        if (!cat.isDefault) {
                            IconButton(onClick = { onDelete(cat) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        TextButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add category")
        }
    }

    if (showAddDialog) {
        CategoryNameDialog(
            title = "Add category",
            onConfirm = { name -> onAdd(name); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }

    renamingCategory?.let { cat ->
        CategoryNameDialog(
            title = "Rename",
            initial = cat.name,
            onConfirm = { name -> onRename(cat, name); renamingCategory = null },
            onDismiss = { renamingCategory = null },
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
