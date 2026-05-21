package io.grimoire.app.ui.screen.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.epub.StagedEpub
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.ALL_TAB_CATEGORY_ID
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibrarySort
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

private val EPUB_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/zip",
    "application/octet-stream",
)

private val STATUS_OPTIONS = listOf(
    -1 to "All",
    1 to "Ongoing",
    2 to "Completed",
    3 to "Hiatus",
    4 to "Cancelled",
    0 to "Unknown",
)

private val SORT_OPTIONS = listOf(
    LibrarySort.LAST_READ_DESC to "Last read",
    LibrarySort.TITLE_ASC to "Title A → Z",
    LibrarySort.TITLE_DESC to "Title Z → A",
    LibrarySort.LAST_UPDATED_DESC to "Last updated (newest)",
    LibrarySort.LAST_UPDATED_ASC to "Last updated (oldest)",
    LibrarySort.UNREAD_DESC to "Unread chapters",
    LibrarySort.TOTAL_DESC to "Total chapters",
)

private fun NovelChapterStats.readPercent(): Int =
    if (total > 0) (readCount * 100 / total).coerceIn(0, 100) else 0

private fun computeTabNovels(
    tabIndex: Int,
    novels: List<NovelEntity>?,
    categories: List<CategoryEntity>,
    showAllTab: Boolean,
    chapterStats: Map<Long, NovelChapterStats>,
    sortOrder: LibrarySort,
    filterStatus: Int,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    isUnlocked: Boolean,
    hiddenCategoryIds: Set<Long>,
    includeHiddenInAll: Boolean,
    searchQuery: String,
): List<NovelEntity>? {
    val loaded = novels ?: return null
    val allTabOffset = if (showAllTab) 1 else 0
    val isAllTab = showAllTab && tabIndex == 0
    val excludeHidden = !isUnlocked || (isAllTab && !includeHiddenInAll)
    val baseFiltered = if (excludeHidden) {
        loaded.filter { it.categoryId !in hiddenCategoryIds }
    } else loaded
    val tabFiltered = when {
        isAllTab -> baseFiltered
        else -> {
            val catIndex = tabIndex - allTabOffset
            val cat = categories.getOrNull(catIndex)
            when {
                cat == null -> baseFiltered
                cat.isDefault -> baseFiltered.filter { it.categoryId == null }
                else -> baseFiltered.filter { it.categoryId == cat.id }
            }
        }
    }
    val comparator: Comparator<NovelEntity> = when (sortOrder) {
        LibrarySort.TITLE_ASC -> Comparator { a: NovelEntity, b: NovelEntity ->
            String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
        }
        LibrarySort.TITLE_DESC -> Comparator { a: NovelEntity, b: NovelEntity ->
            String.CASE_INSENSITIVE_ORDER.compare(b.title, a.title)
        }
        LibrarySort.LAST_UPDATED_DESC -> compareByDescending<NovelEntity> { it.lastUpdated }
        LibrarySort.LAST_UPDATED_ASC -> compareBy<NovelEntity> { it.lastUpdated }
        LibrarySort.UNREAD_DESC -> compareByDescending<NovelEntity> {
            chapterStats[it.id]?.let { s -> s.total - s.readCount } ?: 0
        }
        LibrarySort.TOTAL_DESC -> compareByDescending<NovelEntity> { chapterStats[it.id]?.total ?: 0 }
        LibrarySort.LAST_READ_DESC -> compareByDescending<NovelEntity> { it.lastReadAt }
    }
    val trimmedQuery = searchQuery.trim()
    return tabFiltered
        .filter { novel ->
            (filterStatus == -1 || novel.status == filterStatus) &&
            (!filterUnreadOnly || (chapterStats[novel.id]?.let { it.total - it.readCount > 0 } == true)) &&
            (!filterDownloadedOnly || (chapterStats[novel.id]?.downloadedCount ?: 0) > 0) &&
            (trimmedQuery.isEmpty() ||
                novel.title.contains(trimmedQuery, ignoreCase = true) ||
                (novel.author?.contains(trimmedQuery, ignoreCase = true) == true))
        }
        .sortedWith(comparator)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNovelClick: (pkg: String, url: String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val novels by viewModel.novels.collectAsState()
    val chapterStats by viewModel.chapterStats.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val showAllTab by viewModel.showAllTab.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val filterUnreadOnly by viewModel.filterUnreadOnly.collectAsState()
    val filterDownloadedOnly by viewModel.filterDownloadedOnly.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val hiddenCategoryIds by viewModel.hiddenCategoryIds.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val includeHiddenInAll by viewModel.includeHiddenInAll.collectAsState()
    val staging by viewModel.staging.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val persistedCategoryId by viewModel.persistedCategoryId.collectAsState()
    val categoriesLoaded by viewModel.categoriesLoaded.collectAsState()

    var showManage by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
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
    var showBulkMenu by remember { mutableStateOf(false) }
    val toggleSelect: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    val clearSelection = { selectedIds = emptySet() }

    BackHandler(enabled = selectionMode) { clearSelection() }

    val epubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.stageEpub(uri)
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

    val isFilterActive = filterStatus != -1 || filterUnreadOnly || filterDownloadedOnly

    val tabs = buildList {
        if (showAllTab) add("All")
        addAll(categories.map { it.name })
    }
    // Parallel to `tabs`: the category id behind each tab, or ALL_TAB_CATEGORY_ID for "All".
    val tabCategoryIds = buildList {
        if (showAllTab) add(ALL_TAB_CATEGORY_ID)
        addAll(categories.map { it.id })
    }

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

    LaunchedEffect(pagerState.settledPage) { clearSelection() }

    val defaultCategory = categories.firstOrNull { it.isDefault }

    val novelsForTab: (Int) -> List<NovelEntity>? = { tabIndex ->
        computeTabNovels(
            tabIndex = tabIndex,
            novels = novels,
            categories = categories,
            showAllTab = showAllTab,
            chapterStats = chapterStats,
            sortOrder = sortOrder,
            filterStatus = filterStatus,
            filterUnreadOnly = filterUnreadOnly,
            filterDownloadedOnly = filterDownloadedOnly,
            isUnlocked = isUnlocked,
            hiddenCategoryIds = hiddenCategoryIds,
            includeHiddenInAll = includeHiddenInAll,
            searchQuery = searchQuery,
        )
    }

    val displayedNovels: List<NovelEntity>? = remember(
        novels, currentTab, categories, showAllTab,
        sortOrder, filterStatus, filterUnreadOnly, filterDownloadedOnly, chapterStats,
        isUnlocked, hiddenCategoryIds, includeHiddenInAll, searchQuery,
    ) { novelsForTab(currentTab) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        val ids = displayedNovels?.map { it.id }?.toSet() ?: emptySet()
                        selectedIds = if (selectedIds.containsAll(ids)) emptySet() else ids
                    },
                    menuExpanded = showBulkMenu,
                    onMenuExpandedChange = { showBulkMenu = it },
                    onMove = { showBulkMenu = false; showBulkMove = true },
                    onMarkRead = {
                        showBulkMenu = false
                        viewModel.setNovelsRead(selectedIds, true)
                        clearSelection()
                    },
                    onMarkUnread = {
                        showBulkMenu = false
                        viewModel.setNovelsRead(selectedIds, false)
                        clearSelection()
                    },
                    onDownload = {
                        showBulkMenu = false
                        viewModel.downloadNovels(selectedIds)
                        scope.launch {
                            snackbarHostState.showSnackbar("Queued downloads for ${selectedIds.size} novels")
                        }
                        clearSelection()
                    },
                    onRemove = { showBulkMenu = false; showBulkRemoveConfirm = true },
                )
            } else {
            TopAppBar(
                title = {
                    if (searchActive) {
                        AppSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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
                            searchQuery = ""
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
                        IconButton(onClick = {
                            viewModel.setDisplayMode(
                                if (displayMode == LibraryDisplayMode.GRID) LibraryDisplayMode.LIST
                                else LibraryDisplayMode.GRID
                            )
                        }) {
                            Icon(
                                if (displayMode == LibraryDisplayMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle display mode",
                            )
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(badge = { if (isFilterActive) Badge() }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter & sort")
                            }
                        }
                        IconButton(
                            onClick = { epubPicker.launch(EPUB_MIME_TYPES) },
                            enabled = !importing && !staging,
                        ) {
                            if (importing || staging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.LibraryAdd, contentDescription = "Import EPUB")
                            }
                        }
                        IconButton(onClick = { showManage = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Manage categories")
                        }
                    }
                },
            )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
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
                userScrollEnabled = !selectionMode,
            ) { page ->
                val pageNovels = remember(
                    novels, page, categories, showAllTab,
                    sortOrder, filterStatus, filterUnreadOnly, filterDownloadedOnly, chapterStats,
                    isUnlocked, hiddenCategoryIds, includeHiddenInAll, searchQuery,
                ) { novelsForTab(page) }

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
                sortOrder = sortOrder,
                filterStatus = filterStatus,
                filterUnreadOnly = filterUnreadOnly,
                filterDownloadedOnly = filterDownloadedOnly,
                onSortChange = viewModel::setSortOrder,
                onFilterStatusChange = viewModel::setFilterStatus,
                onUnreadOnlyChange = viewModel::setFilterUnreadOnly,
                onDownloadedOnlyChange = viewModel::setFilterDownloadedOnly,
            )
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
        MoveToCategoryDialog(
            categories = categories,
            defaultCategory = defaultCategory,
            currentCategoryId = null,
            onSelect = { catId ->
                viewModel.moveNovels(selectedIds, catId)
                showBulkMove = false
                clearSelection()
            },
            onDismiss = { showBulkMove = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMove: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download chapters")
            }
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("Move to category") },
                        onClick = onMove,
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark as read") },
                        onClick = onMarkRead,
                        leadingIcon = { Icon(Icons.Default.DoneAll, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark as unread") },
                        onClick = onMarkUnread,
                        leadingIcon = { Icon(Icons.Default.RemoveDone, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Download chapters") },
                        onClick = onDownload,
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from library",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = onRemove,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        },
    )
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
    sortOrder: LibrarySort,
    filterStatus: Int,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    onSortChange: (LibrarySort) -> Unit,
    onFilterStatusChange: (Int) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Filter") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sort") })
        }
        when (tab) {
            0 -> FilterTab(filterStatus, filterUnreadOnly, filterDownloadedOnly, onFilterStatusChange, onUnreadOnlyChange, onDownloadedOnlyChange)
            1 -> SortTab(sortOrder, onSortChange)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FilterTab(
    filterStatus: Int,
    filterUnreadOnly: Boolean,
    filterDownloadedOnly: Boolean,
    onFilterStatusChange: (Int) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
) {
    Column {
        Text(
            "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(STATUS_OPTIONS) { (ordinal, label) ->
                FilterChip(
                    selected = filterStatus == ordinal,
                    onClick = {
                        onFilterStatusChange(if (filterStatus == ordinal && ordinal != -1) -1 else ordinal)
                    },
                    label = { Text(label) },
                )
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
    sortOrder: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SORT_OPTIONS.forEach { (sort, label) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = {
                    RadioButton(
                        selected = sortOrder == sort,
                        onClick = { onSortChange(sort) },
                    )
                },
                modifier = Modifier.clickable { onSortChange(sort) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelCard(
    novel: NovelEntity,
    stats: NovelChapterStats?,
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
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.White, RoundedCornerShape(50))
                            .size(22.dp),
                    )
                }
                if (stats != null && stats.total > 0) {
                    val percent = stats.readPercent()
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "${stats.readCount}/${stats.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                if (stats != null && stats.downloadedCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "${stats.downloadedCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
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
            supportingContent = if (stats != null && stats.total > 0) {
                {
                    val percent = stats.readPercent()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${stats.readCount}/${stats.total} ($percent%)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (stats.downloadedCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "${stats.downloadedCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            } else if (!novel.author.isNullOrBlank()) {
                { Text(novel.author!!, maxLines = 1) }
            } else null,
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
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}

@Composable
private fun MoveToCategoryDialog(
    categories: List<CategoryEntity>,
    defaultCategory: CategoryEntity?,
    currentCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to category") },
        text = {
            Column {
                categories.forEach { cat ->
                    val targetId = if (cat.isDefault) null else cat.id
                    val isSelected = if (cat.isDefault) currentCategoryId == null else currentCategoryId == cat.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(targetId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelect(targetId) })
                        Spacer(Modifier.width(8.dp))
                        Text(cat.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
