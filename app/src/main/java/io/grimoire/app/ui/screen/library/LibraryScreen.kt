package io.grimoire.app.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibrarySort
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNovelClick: (pkg: String, url: String) -> Unit,
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

    var selectedTab by remember { mutableIntStateOf(0) }
    var showManage by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val filterSheetState = rememberModalBottomSheetState()

    val isFilterActive = filterStatus != -1 || filterUnreadOnly || filterDownloadedOnly

    val tabs = buildList {
        if (showAllTab) add("All")
        addAll(categories.map { it.name })
    }

    val effectiveTab = selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    LaunchedEffect(tabs.size) {
        if (selectedTab >= tabs.size) selectedTab = 0
    }

    val defaultCategory = categories.firstOrNull { it.isDefault }

    val displayedNovels: List<NovelEntity>? = remember(
        novels, effectiveTab, categories, showAllTab,
        sortOrder, filterStatus, filterUnreadOnly, filterDownloadedOnly, chapterStats,
        isUnlocked, hiddenCategoryIds, includeHiddenInAll,
    ) {
        val loaded = novels ?: return@remember null
        val allTabOffset = if (showAllTab) 1 else 0
        val isAllTab = showAllTab && effectiveTab == 0
        val excludeHidden = !isUnlocked || (isAllTab && !includeHiddenInAll)
        val baseFiltered = if (excludeHidden) {
            loaded.filter { it.categoryId !in hiddenCategoryIds }
        } else loaded
        val tabFiltered = when {
            isAllTab -> baseFiltered
            else -> {
                val catIndex = effectiveTab - allTabOffset
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
        tabFiltered
            .filter { novel ->
                (filterStatus == -1 || novel.status == filterStatus) &&
                (!filterUnreadOnly || (chapterStats[novel.id]?.let { it.total - it.readCount > 0 } == true)) &&
                (!filterDownloadedOnly || (chapterStats[novel.id]?.downloadedCount ?: 0) > 0)
            }
            .sortedWith(comparator)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Library",
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { if (hasPin && !isUnlocked) showUnlock = true },
                        ),
                    )
                },
                actions = {
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
                    IconButton(onClick = { showManage = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Manage categories")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (tabs.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = effectiveTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = effectiveTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }

            when {
                displayedNovels == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                displayedNovels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No novels here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val onNovelClickWrapped: (NovelEntity) -> Unit = { novel ->
                        val pkg = viewModel.pkgForNovel(novel)
                        if (pkg.isNotEmpty()) onNovelClick(pkg, novel.url)
                        else scope.launch { snackbarHostState.showSnackbar("Extension not installed") }
                    }
                    if (displayMode == LibraryDisplayMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(displayedNovels, key = { it.id }) { novel ->
                                NovelCard(
                                    novel = novel,
                                    stats = chapterStats[novel.id],
                                    categories = categories,
                                    defaultCategory = defaultCategory,
                                    onClick = { onNovelClickWrapped(novel) },
                                    onMove = { categoryId -> viewModel.moveNovel(novel, categoryId) },
                                    onRemove = { viewModel.removeFromLibrary(novel) },
                                )
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(displayedNovels, key = { it.id }) { novel ->
                                NovelRow(
                                    novel = novel,
                                    stats = chapterStats[novel.id],
                                    categories = categories,
                                    defaultCategory = defaultCategory,
                                    onClick = { onNovelClickWrapped(novel) },
                                    onMove = { categoryId -> viewModel.moveNovel(novel, categoryId) },
                                    onRemove = { viewModel.removeFromLibrary(novel) },
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
    categories: List<CategoryEntity>,
    defaultCategory: CategoryEntity?,
    onClick: () -> Unit,
    onMove: (Long?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(
            Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
        ) {
            Box {
                AsyncImage(
                    model = novel.thumbnailUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                )
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
                            .align(Alignment.BottomEnd)
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

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Move to category") },
                onClick = { showMoveDialog = true; showMenu = false },
            )
            DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = { onRemove(); showMenu = false },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
            )
        }
    }

    if (showMoveDialog) {
        MoveToCategoryDialog(
            categories = categories,
            defaultCategory = defaultCategory,
            currentCategoryId = novel.categoryId,
            onSelect = { catId -> onMove(catId); showMoveDialog = false },
            onDismiss = { showMoveDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelRow(
    novel: NovelEntity,
    stats: NovelChapterStats?,
    categories: List<CategoryEntity>,
    defaultCategory: CategoryEntity?,
    onClick: () -> Unit,
    onMove: (Long?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    Box(modifier) {
        ListItem(
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
                AsyncImage(
                    model = novel.thumbnailUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp)),
                )
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Move to category") },
                onClick = { showMoveDialog = true; showMenu = false },
            )
            DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = { onRemove(); showMenu = false },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
            )
        }
    }

    if (showMoveDialog) {
        MoveToCategoryDialog(
            categories = categories,
            defaultCategory = defaultCategory,
            currentCategoryId = novel.categoryId,
            onSelect = { catId -> onMove(catId); showMoveDialog = false },
            onDismiss = { showMoveDialog = false },
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
        categories.forEach { cat ->
            ListItem(
                headlineContent = { Text(cat.name) },
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
