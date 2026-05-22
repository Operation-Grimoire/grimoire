package io.grimoire.app.ui.screen.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.domain.migration.MigrationState
import io.grimoire.app.ui.component.FastScroller
import io.grimoire.app.ui.component.ShimmerBox
import io.grimoire.app.ui.component.ExpandableText
import io.grimoire.app.ui.component.GenreChips
import io.grimoire.app.ui.component.MoveToCategorySheet
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.ui.component.ZoomableCoverImage
import io.grimoire.app.ui.component.rememberShimmerAlpha
import io.grimoire.app.ui.theme.premiumGold
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NovelDetailScreen(
    onNavigateBack: () -> Unit,
    onChapterClick: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit = { _, _, _ -> },
    onOpenWebView: (url: String) -> Unit = {},
    onOpenNuSeries: (slug: String) -> Unit = {},
    onNavigateToLogin: (pkg: String) -> Unit = {},
    onMigrate: (novelId: Long) -> Unit = {},
    onMigrationComplete: (pkg: String, url: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: NovelDetailViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val isLoadingNovel by viewModel.isLoadingNovel.collectAsState()
    val isLoadingChapters by viewModel.isLoadingChapters.collectAsState()
    val novelError by viewModel.novelError.collectAsState()
    val chaptersError by viewModel.chaptersError.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val novelId by viewModel.novelId.collectAsState()
    val chapterPage by viewModel.chapterPage.collectAsState()
    val chapterSort by viewModel.chapterSort.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val bookDownload by viewModel.bookDownload.collectAsState()
    val nuState by viewModel.nuState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val hasLockedChapters by viewModel.hasLockedChapters.collectAsState()
    val migrationState by viewModel.migrationState.collectAsState()
    val migrateFromTitle by viewModel.migrateFromTitle.collectAsState()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showMigrateConfirm by remember { mutableStateOf(false) }
    var migrateMatchCount by remember { mutableStateOf(0) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var lockedDialogChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showJumpDialog by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val clearSelection = { selectedIds = emptySet() }
    val toggleSelect: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    BackHandler(enabled = selectionMode) { clearSelection() }
    val selectedChapters = chapters.filter { it.id in selectedIds }

    val hasUploadDates by remember(chapters) {
        derivedStateOf { chapters.any { it.uploadDate > 0L } }
    }

    val continueChapter by remember(chapters) {
        derivedStateOf {
            val sorted = chapters.sortedBy { it.chapterNumber }
            // Don't send the reader into a locked chapter from the Continue FAB.
            sorted.firstOrNull { !it.read && !it.locked }
                ?: sorted.lastOrNull { !it.locked }
                ?: sorted.lastOrNull()
        }
    }

    // Nudge the user to sign in only when it would actually help: there are
    // locked chapters and the source supports login but isn't signed in.
    val showLoginBanner = loginState == LoginState.SIGNED_OUT && hasLockedChapters

    val displayedChapters by remember(chapters, chapterSort, searchQuery) {
        derivedStateOf {
            val sorted = when (chapterSort) {
                ChapterSort.NUMBER_ASC -> chapters
                ChapterSort.NUMBER_DESC -> chapters.reversed()
                ChapterSort.DATE_ASC -> chapters.sortedBy { it.uploadDate }
                ChapterSort.DATE_DESC -> chapters.sortedByDescending { it.uploadDate }
            }
            if (searchQuery.isBlank()) sorted
            else sorted.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex < 2 } }

    // Number of LazyColumn items before chapter items — used for fast scroller label
    val chapterHeaderOffset by remember(isLoadingNovel, novelError, novel, chaptersError, isFavorite, categories, nuState, showLoginBanner) {
        derivedStateOf {
            var count = 1 // novel header / skeleton / error
            if (!isLoadingNovel && novelError == null) {
                if (isFavorite && categories.isNotEmpty()) count++ // category row
                if (novel.genres.isNotEmpty()) count++
                if (!novel.description.isNullOrBlank()) count++
            }
            if (nuState !is NuInfoState.Idle && nuState !is NuInfoState.Disabled) count++ // NovelUpdates section
            count++ // chapter controls row
            if (chaptersError != null) count++
            if (showLoginBanner) count++ // locked-chapters login banner
            count
        }
    }

    if (showCategoryDialog && categories.isNotEmpty()) {
        MoveToCategorySheet(
            categories = categories,
            currentCategoryId = categoryId,
            count = 1,
            onSelect = { targetId ->
                viewModel.setCategory(targetId)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false },
        )
    }

    if (showJumpDialog) {
        val nextUnread = continueChapter?.takeIf { !it.read }
        JumpDialog(
            nextUnreadLabel = nextUnread?.let { ch ->
                ch.name.ifBlank {
                    val n = ch.chapterNumber
                    if (n > 0f) {
                        val pretty = if (n % 1f == 0f) n.toInt().toString() else n.toString()
                        "Chapter $pretty"
                    } else "Next chapter"
                }
            },
            onJumpToNextUnread = {
                val target = nextUnread
                if (target != null) {
                    val idx = displayedChapters.indexOfFirst { it.url == target.url }
                    if (idx >= 0) coroutineScope.launch {
                        listState.scrollToItem(chapterHeaderOffset + idx)
                    }
                }
                showJumpDialog = false
            },
            onDismiss = { showJumpDialog = false },
            onJump = { target ->
                val idx = displayedChapters.indexOfFirst { ch ->
                    ch.chapterNumber.toInt() == target ||
                        ch.name.contains(target.toString(), ignoreCase = true)
                }
                if (idx >= 0) coroutineScope.launch {
                    listState.scrollToItem(chapterHeaderOffset + idx)
                }
                showJumpDialog = false
            },
        )
    }

    lockedDialogChapter?.let { locked ->
        AlertDialog(
            onDismissRequest = { lockedDialogChapter = null },
            icon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.premiumGold,
                )
            },
            title = { Text("Chapter locked") },
            text = {
                Text(
                    "\"${locked.name}\" is locked. Reading it requires a " +
                        "${viewModel.sourceName} account that has purchased these " +
                        "chapters. Log in to read the chapters your account has unlocked.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    lockedDialogChapter = null
                    onNavigateToLogin(viewModel.pkg)
                }) { Text("Log in") }
            },
            dismissButton = {
                TextButton(onClick = { lockedDialogChapter = null }) { Text("Close") }
            },
        )
    }

    LaunchedEffect(migrationState) {
        if (migrationState == MigrationState.Success) onMigrationComplete(viewModel.pkg, novel.url)
    }

    if (showMigrateConfirm) {
        AlertDialog(
            onDismissRequest = { showMigrateConfirm = false },
            title = { Text("Migrate to this novel?") },
            text = {
                Text(
                    (if (migrateMatchCount > 0) {
                        "$migrateMatchCount chapter${if (migrateMatchCount == 1) "" else "s"} " +
                            "will be marked as read here."
                    } else {
                        "No chapters could be matched, so no read progress will carry over."
                    }) + " \"$migrateFromTitle\" will be removed from your library.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMigrateConfirm = false
                    viewModel.confirmMigration()
                }) { Text("Migrate") }
            },
            dismissButton = {
                TextButton(onClick = { showMigrateConfirm = false }) { Text("Cancel") }
            },
        )
    }

    (migrationState as? MigrationState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMigrationError,
            title = { Text("Migration failed") },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMigrationError) { Text("OK") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (continueChapter != null && !viewModel.isMigrationTarget && !selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onChapterClick(viewModel.pkg, novel.url, continueChapter!!.url)
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text(if (chapters.none { it.read }) "Start" else "Continue") },
                    expanded = fabExpanded,
                )
            }
        },
        topBar = {
            if (selectionMode) {
                ChapterSelectionTopBar(
                    count = selectedIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        val ids = displayedChapters.map { it.id }.toSet()
                        selectedIds = if (selectedIds.containsAll(ids)) emptySet() else ids
                    },
                )
            } else {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (!viewModel.isLocal) {
                        IconButton(onClick = { onOpenWebView(viewModel.novelWebUrl) }) {
                            Icon(Icons.Default.Language, contentDescription = "Open in WebView")
                        }
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isFavorite) "Remove from library" else "Add to library",
                        )
                    }
                    val hasBulkActions = chapters.isNotEmpty()
                    val canMigrate = isFavorite && novelId > 0L
                    if (hasBulkActions || canMigrate) {
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                            ) {
                                if (hasBulkActions) {
                                    DropdownMenuItem(
                                        text = { Text("Mark all as read") },
                                        onClick = { viewModel.markAllRead(true); overflowMenuExpanded = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mark all as unread") },
                                        onClick = { viewModel.markAllRead(false); overflowMenuExpanded = false },
                                    )
                                    if (!viewModel.isLocal) {
                                        DropdownMenuItem(
                                            text = { Text("Download all") },
                                            onClick = { viewModel.downloadAll(); overflowMenuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Download, null) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Download unread") },
                                            onClick = { viewModel.downloadUnread(); overflowMenuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Download, null) },
                                        )
                                    }
                                    if (chapters.any { it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal }) {
                                        DropdownMenuItem(
                                            text = { Text("Cancel all downloads") },
                                            onClick = { viewModel.cancelAllDownloads(); overflowMenuExpanded = false },
                                            leadingIcon = { Icon(Icons.Default.Close, null) },
                                        )
                                    }
                                }
                                if (canMigrate) {
                                    if (hasBulkActions) HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Migrate") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            onMigrate(novelId)
                                        },
                                        leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                                    )
                                }
                            }
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            Column {
                if (!selectionMode && viewModel.isMigrationTarget && novelId > 0L &&
                    novelId != viewModel.migrateFromId
                ) {
                    Surface(shadowElevation = 8.dp) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    migrateMatchCount = viewModel.migrationMatchCount()
                                    showMigrateConfirm = true
                                }
                            },
                            enabled = migrationState != MigrationState.Running && chapters.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                if (migrationState == MigrationState.Running) "Migrating…"
                                else "Migrate here",
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = selectionMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ChapterSelectionBottomBar(
                        showMarkRead = selectedChapters.any { !it.read },
                        showMarkUnread = selectedChapters.any { it.read },
                        showDownload = selectedChapters.any {
                            !it.locked &&
                                (it.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
                                    it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal)
                        },
                        showDelete = selectedChapters.any {
                            it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal
                        },
                        showCancel = selectedChapters.any {
                            it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal
                        },
                        singleSelection = selectedIds.size == 1,
                        onMarkRead = {
                            viewModel.markChaptersRead(selectedIds.toList(), true)
                            clearSelection()
                        },
                        onMarkUnread = {
                            viewModel.markChaptersRead(selectedIds.toList(), false)
                            clearSelection()
                        },
                        onDownload = {
                            viewModel.downloadChapters(selectedChapters)
                            clearSelection()
                        },
                        onDeleteDownloads = {
                            viewModel.deleteDownloads(selectedChapters)
                            clearSelection()
                        },
                        onCancelDownloads = {
                            viewModel.cancelDownloads(selectedChapters)
                            clearSelection()
                        },
                        onSelectAbove = {
                            val idx = displayedChapters.indexOfFirst { it.id in selectedIds }
                            if (idx >= 0) {
                                selectedIds = selectedIds +
                                    displayedChapters.subList(0, idx + 1).map { it.id }
                            }
                        },
                        onSelectBelow = {
                            val idx = displayedChapters.indexOfFirst { it.id in selectedIds }
                            if (idx >= 0) {
                                selectedIds = selectedIds +
                                    displayedChapters.subList(idx, displayedChapters.size).map { it.id }
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        val detailBody = @Composable {
            FastScroller(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                thumbLabel = { fraction ->
                    val rawIdx = (fraction * listState.layoutInfo.totalItemsCount).toInt()
                    val chapterIdx = (rawIdx - chapterHeaderOffset).coerceIn(0, displayedChapters.size - 1)
                    displayedChapters.getOrNull(chapterIdx)?.name
                },
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom = if (continueChapter != null && !selectionMode) 88.dp else 0.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Novel header
                    item(key = "novel_header") {
                        when {
                            isLoadingNovel -> NovelHeaderSkeleton()
                            novelError != null -> Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(novelError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = viewModel::retryNovel) { Text("Retry") }
                            }
                            else -> NovelHeader(novel = novel, sourceName = viewModel.sourceName, isLocal = viewModel.isLocal)
                        }
                    }

                    // Category (when in library)
                    if (!isLoadingNovel && novelError == null && isFavorite && categories.isNotEmpty()) {
                        item(key = "category") {
                            val currentCat = categories.firstOrNull { cat ->
                                if (cat.isDefault) categoryId == null else cat.id == categoryId
                            }
                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable { showCategoryDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Category: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    currentCat?.name ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // Genres
                    if (!isLoadingNovel && novelError == null && novel.genres.isNotEmpty()) {
                        item(key = "genres") {
                            GenreChips(
                                genres = novel.genres,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Description
                    if (!isLoadingNovel && novelError == null && !novel.description.isNullOrBlank()) {
                        item(key = "description") {
                            ExpandableText(
                                text = novel.description!!,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // NovelUpdates metadata + recommendations
                    if (nuState !is NuInfoState.Idle && nuState !is NuInfoState.Disabled) {
                        item(key = "novelupdates") {
                            NovelUpdatesSection(
                                state = nuState,
                                viewModel = viewModel,
                                onOpenWebView = onOpenWebView,
                                onOpenNuSeries = onOpenNuSeries,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Whole-book EPUB download (EpubSource only)
                    if (!isLoadingNovel && novelError == null && viewModel.isEpubSource) {
                        item(key = "epub_download") {
                            Column(
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val downloading = bookDownload is BookDownloadState.Downloading
                                Button(
                                    onClick = { viewModel.downloadBook() },
                                    enabled = !downloading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (downloading) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Downloading…")
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (chapters.isEmpty()) "Download EPUB" else "Re-download EPUB")
                                    }
                                }
                                (bookDownload as? BookDownloadState.Error)?.let { err ->
                                    Text(
                                        err.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Chapter controls
                    item(key = "chapter_controls") {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = when {
                                        isLoadingChapters && chapterPage > 0 -> "Loading page $chapterPage…"
                                        isLoadingChapters -> "Loading chapters…"
                                        chaptersError != null -> "Chapters"
                                        searchQuery.isNotBlank() -> "${displayedChapters.size} of ${chapters.size}"
                                        else -> "${chapters.size} chapter${if (chapters.size != 1) "s" else ""}"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = chapters.isNotEmpty()) { showJumpDialog = true },
                                )
                                if (chapters.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchActive = !searchActive
                                        if (!searchActive) searchQuery = ""
                                    }) {
                                        Icon(
                                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = if (searchActive) "Close search" else "Search chapters",
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { sortMenuExpanded = true }) {
                                            Icon(Icons.Default.SwapVert, contentDescription = "Sort options")
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false },
                                        ) {
                                            ChapterSort.entries.forEach { sort ->
                                                val dateSort = sort == ChapterSort.DATE_ASC || sort == ChapterSort.DATE_DESC
                                                DropdownMenuItem(
                                                    text = { Text(sort.label) },
                                                    onClick = { viewModel.setSort(sort); sortMenuExpanded = false },
                                                    enabled = !dateSort || hasUploadDates,
                                                    trailingIcon = if (chapterSort == sort) {
                                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                                    } else null,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (isLoadingChapters) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                            if (!isLoadingChapters && chaptersError == null && chapters.isNotEmpty()) {
                                val readCount = chapters.count { it.read }
                                val downloadedCount = chapters.count {
                                    it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal
                                }
                                val lockedCount = chapters.count { it.locked }
                                val percent = (readCount * 100 / chapters.size).coerceIn(0, 100)
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "$readCount / ${chapters.size} ($percent%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (downloadedCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.DownloadDone,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = "$downloadedCount",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    if (lockedCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Locked chapters",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.premiumGold,
                                            )
                                            Text(
                                                text = "$lockedCount",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(visible = searchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search chapters…") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                )
                            }
                        }
                    }

                    // Chapters error
                    if (chaptersError != null) {
                        item(key = "chapters_error") {
                            Column(
                                modifier = Modifier.animateItem().fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(chaptersError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = viewModel::retryChapters) { Text("Retry") }
                            }
                        }
                    }

                    // Locked-chapters login nudge
                    if (showLoginBanner) {
                        item(key = "locked_login_banner") {
                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onNavigateToLogin(viewModel.pkg) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    "Some chapters are locked. Log in to ${viewModel.sourceName} " +
                                        "to read the chapters your account has unlocked.",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = { onNavigateToLogin(viewModel.pkg) }) {
                                    Text("Log in")
                                }
                            }
                        }
                    }

                    // Chapter list or skeleton
                    if (isLoadingChapters && chapters.isEmpty()) {
                        item(key = "skeletons") {
                            val alpha = rememberShimmerAlpha()
                            Column {
                                repeat(14) { ChapterSkeletonItem(alpha = alpha) }
                            }
                        }
                    } else {
                        items(displayedChapters, key = { it.url }) { chapter ->
                            ChapterItem(
                                chapter = chapter,
                                selected = chapter.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = { onChapterClick(viewModel.pkg, novel.url, chapter.url) },
                                onLockedClick = { lockedDialogChapter = chapter },
                                onToggleSelection = { toggleSelect(chapter.id) },
                                onDownload = { viewModel.downloadChapter(chapter) },
                                onCancelDownload = { viewModel.cancelDownload(chapter) },
                                onDeleteDownload = { viewModel.deleteDownload(chapter) },
                            )
                        }
                    }
                }
            }
        }
        if (viewModel.isLocal) {
            Box(Modifier.padding(padding)) { detailBody() }
        } else {
            PullToRefreshBox(
                isRefreshing = isLoadingNovel && novel.initialized,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding),
            ) { detailBody() }
        }
    }
}

@Composable
private fun JumpDialog(
    nextUnreadLabel: String?,
    onJumpToNextUnread: () -> Unit,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to chapter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nextUnreadLabel != null) {
                    AssistChip(
                        onClick = onJumpToNextUnread,
                        label = {
                            Text(
                                "Next unread: $nextUnreadLabel",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        },
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("Chapter number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { input.toIntOrNull()?.let(onJump) }),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { input.toIntOrNull()?.let(onJump) },
                enabled = input.isNotBlank(),
            ) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NovelHeaderSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBox(modifier = Modifier.width(120.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(8.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(20.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp))
        }
    }
}

@Composable
private fun ChapterSkeletonItem(alpha: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(15.dp), alpha = alpha)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(11.dp), alpha = alpha)
        }
        ShimmerBox(modifier = Modifier.size(20.dp), shape = CircleShape, alpha = alpha)
    }
}

@Composable
private fun NovelHeader(novel: Novel, sourceName: String = "", isLocal: Boolean = false, modifier: Modifier = Modifier) {
    var showRatingInfo by remember { mutableStateOf(false) }

    if (showRatingInfo) {
        AlertDialog(
            onDismissRequest = { showRatingInfo = false },
            title = { Text("About this rating") },
            text = {
                Text(
                    "This rating is reported by ${sourceName.ifBlank { "the source" }} " +
                        "and reflects readers there — not your activity in Grimoire.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showRatingInfo = false }) { Text("Got it") }
            },
        )
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ZoomableCoverImage(
            model = novel.thumbnailUrl,
            contentDescription = novel.title,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(novel.title, style = MaterialTheme.typography.titleLarge)
            if (!novel.author.isNullOrBlank()) {
                Text(novel.author!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusLabel(status = novel.status)
                novel.rating?.let {
                    RatingLabel(
                        rating = it,
                        count = novel.ratingCount,
                        onClick = { showRatingInfo = true },
                    )
                }
            }
            if (isLocal) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("EPUB") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            } else if (sourceName.isNotBlank()) {
                val lang = novel.language?.trim().orEmpty()
                Text(
                    if (lang.isNotEmpty()) "$sourceName · $lang" else sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusLabel(status: NovelStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            status.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RatingLabel(
    rating: Float,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = rating.coerceIn(0f, 5f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = buildString {
                append(String.format(Locale.getDefault(), "%.1f", clamped))
                if (count != null && count > 0) append(" (").append(count).append(')')
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterItem(
    chapter: ChapterEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read chapters dim to signal "done"; locked chapters instead use a gold
    // accent to signal "premium", so the two states stay visually distinct.
    val contentAlpha = if (chapter.read && !chapter.locked) 0.38f else 1f
    val headlineColor = if (chapter.locked) {
        MaterialTheme.colorScheme.premiumGold
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
    }
    val dateText = remember(chapter.uploadDate) { if (chapter.uploadDate > 0L) formatDate(chapter.uploadDate) else null }
    val progressText = if (!chapter.read && chapter.readProgress > 0f) "${(chapter.readProgress * 100).toInt()}%" else null
    val subText = listOfNotNull(dateText, progressText).joinToString(" · ").takeIf { it.isNotEmpty() }
    val dlStatus = ChapterDownloadStatus.entries.getOrElse(chapter.downloadStatus) { ChapterDownloadStatus.NONE }

    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        } else {
            ListItemDefaults.colors()
        },
        headlineContent = {
            Text(
                chapter.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = headlineColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = if (subText != null) {
            {
                Text(
                    subText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else null,
        trailingContent = {
            // Non-interactive in selection mode so a row tap toggles selection.
            when {
                chapter.locked -> ChapterTrailingIcon(
                    icon = Icons.Default.Lock,
                    description = "Locked",
                    tint = MaterialTheme.colorScheme.premiumGold,
                    onClick = if (selectionMode) null else onLockedClick,
                )
                dlStatus == ChapterDownloadStatus.NONE -> ChapterTrailingIcon(
                    icon = Icons.Default.Download,
                    description = "Download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = if (selectionMode) null else onDownload,
                )
                dlStatus == ChapterDownloadStatus.QUEUED -> ChapterTrailingIcon(
                    icon = Icons.Default.Close,
                    description = "Cancel download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = if (selectionMode) null else onCancelDownload,
                )
                dlStatus == ChapterDownloadStatus.DOWNLOADING ->
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        LinearProgressIndicator(modifier = Modifier.width(24.dp))
                    }
                dlStatus == ChapterDownloadStatus.DOWNLOADED -> ChapterTrailingIcon(
                    icon = Icons.Default.DownloadDone,
                    description = "Delete download",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = if (selectionMode) null else onDeleteDownload,
                )
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    ChapterTrailingIcon(
                        icon = Icons.Default.Close,
                        description = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = if (selectionMode) null else onDeleteDownload,
                        buttonSize = 40.dp,
                        iconSize = 20.dp,
                    )
                    ChapterTrailingIcon(
                        icon = Icons.Default.Refresh,
                        description = "Retry",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = if (selectionMode) null else onDownload,
                        buttonSize = 40.dp,
                        iconSize = 20.dp,
                    )
                }
            }
        },
        modifier = modifier.combinedClickable(
            onClick = when {
                selectionMode -> onToggleSelection
                chapter.locked -> onLockedClick
                else -> onClick
            },
            onLongClick = onToggleSelection,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
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
        },
    )
}

/** Contextual action bar shown at the bottom while chapters are selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSelectionBottomBar(
    showMarkRead: Boolean,
    showMarkUnread: Boolean,
    showDownload: Boolean,
    showDelete: Boolean,
    showCancel: Boolean,
    singleSelection: Boolean,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onCancelDownloads: () -> Unit,
    onSelectAbove: () -> Unit,
    onSelectBelow: () -> Unit,
) {
    BottomAppBar {
        // Holding an action grows its weight, so it gains room for the label
        // while the others give way as the row reflows.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMarkRead) {
                TooltipIconButton(
                    icon = Icons.Default.DoneAll,
                    label = "Mark read",
                    onClick = onMarkRead,
                )
            }
            if (showMarkUnread) {
                TooltipIconButton(
                    icon = Icons.Default.RemoveDone,
                    label = "Mark unread",
                    onClick = onMarkUnread,
                )
            }
            if (showDownload) {
                TooltipIconButton(
                    icon = Icons.Default.Download,
                    label = "Download",
                    onClick = onDownload,
                )
            }
            if (showDelete) {
                TooltipIconButton(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    onClick = onDeleteDownloads,
                )
            }
            if (showCancel) {
                TooltipIconButton(
                    icon = Icons.Default.Close,
                    label = "Cancel",
                    onClick = onCancelDownloads,
                )
            }
            if (singleSelection) {
                TooltipIconButton(
                    icon = Icons.Default.VerticalAlignTop,
                    label = "Select above",
                    onClick = onSelectAbove,
                )
                TooltipIconButton(
                    icon = Icons.Default.VerticalAlignBottom,
                    label = "Select below",
                    onClick = onSelectBelow,
                )
            }
        }
    }
}

// A null onClick renders a plain, non-interactive icon with the same footprint.
@Composable
private fun ChapterTrailingIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: (() -> Unit)?,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    if (onClick != null) {
        IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
            Icon(icon, contentDescription = description, tint = tint,
                modifier = Modifier.size(iconSize))
        }
    } else {
        Box(Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint,
                modifier = Modifier.size(iconSize))
        }
    }
}

private val NovelStatus.displayName: String
    get() = when (this) {
        NovelStatus.UNKNOWN -> "Unknown"
        NovelStatus.ONGOING -> "Ongoing"
        NovelStatus.COMPLETED -> "Completed"
        NovelStatus.HIATUS -> "Hiatus"
        NovelStatus.CANCELLED -> "Cancelled"
    }

private val NovelStatus.icon: ImageVector
    get() = when (this) {
        NovelStatus.UNKNOWN -> Icons.Default.HelpOutline
        NovelStatus.ONGOING -> Icons.Default.Schedule
        NovelStatus.COMPLETED -> Icons.Default.CheckCircle
        NovelStatus.HIATUS -> Icons.Default.PauseCircle
        NovelStatus.CANCELLED -> Icons.Default.Block
    }

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
