package io.grimoire.app.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.LibraryDisplayMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNovelClick: (pkg: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val novels by viewModel.novels.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val showAllTab by viewModel.showAllTab.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showManage by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val tabs = buildList {
        if (showAllTab) add("All")
        addAll(categories.map { it.name })
    }

    LaunchedEffect(tabs.size) {
        if (selectedTab >= tabs.size) selectedTab = 0
    }

    val defaultCategory = categories.firstOrNull { it.isDefault }

    val displayedNovels = remember(novels, selectedTab, categories, showAllTab) {
        val allTabOffset = if (showAllTab) 1 else 0
        if (showAllTab && selectedTab == 0) novels
        else {
            val catIndex = selectedTab - allTabOffset
            val cat = categories.getOrNull(catIndex) ?: return@remember novels
            if (cat.isDefault) novels.filter { it.categoryId == null }
            else novels.filter { it.categoryId == cat.id }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
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
                    IconButton(onClick = { showManage = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Manage categories")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (tabs.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }

            if (displayedNovels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No novels here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
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

    if (showManage) {
        ModalBottomSheet(
            onDismissRequest = { showManage = false },
            sheetState = sheetState,
        ) {
            ManageCategoriesSheet(
                categories = categories,
                onAdd = viewModel::addCategory,
                onRename = { cat, name -> viewModel.renameCategory(cat, name) },
                onDelete = viewModel::deleteCategory,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelCard(
    novel: NovelEntity,
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
            AsyncImage(
                model = novel.thumbnailUrl,
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
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
            supportingContent = if (!novel.author.isNullOrBlank()) {
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
    onAdd: (String) -> Unit,
    onRename: (CategoryEntity, String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
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
        categories.forEach { cat ->
            ListItem(
                headlineContent = { Text(cat.name) },
                trailingContent = {
                    Row {
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
