package io.grimoire.app.ui.screen.browse

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.ui.component.FastScroller
import io.grimoire.app.ui.component.ShimmerBox
import io.grimoire.app.ui.component.ExpandableText
import io.grimoire.app.ui.component.GenreChips
import io.grimoire.app.ui.component.ZoomableCoverImage
import io.grimoire.app.ui.component.rememberShimmerAlpha
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
    val chapterPage by viewModel.chapterPage.collectAsState()
    val chapterSort by viewModel.chapterSort.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val bookDownload by viewModel.bookDownload.collectAsState()
    val nuState by viewModel.nuState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val hasLockedChapters by viewModel.hasLockedChapters.collectAsState()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var lockedDialogChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var bulkMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showJumpDialog by remember { mutableStateOf(false) }

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
        val defaultCat = categories.firstOrNull { it.isDefault }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Move to category") },
            text = {
                Column {
                    categories.forEach { cat ->
                        val targetId = if (cat.isDefault) null else cat.id
                        val isSelected = if (cat.isDefault) categoryId == null else categoryId == cat.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setCategory(targetId); showCategoryDialog = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setCategory(targetId); showCategoryDialog = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") } },
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
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
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

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (continueChapter != null) {
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
                },
            )
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
                    contentPadding = PaddingValues(bottom = if (continueChapter != null) 88.dp else 0.dp),
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
                                    Box {
                                        IconButton(onClick = { bulkMenuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                        }
                                        DropdownMenu(
                                            expanded = bulkMenuExpanded,
                                            onDismissRequest = { bulkMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Mark all as read") },
                                                onClick = { viewModel.markAllRead(true); bulkMenuExpanded = false },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Mark all as unread") },
                                                onClick = { viewModel.markAllRead(false); bulkMenuExpanded = false },
                                            )
                                            if (!viewModel.isLocal) {
                                                DropdownMenuItem(
                                                    text = { Text("Download all") },
                                                    onClick = { viewModel.downloadAll(); bulkMenuExpanded = false },
                                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Download unread") },
                                                    onClick = { viewModel.downloadUnread(); bulkMenuExpanded = false },
                                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                                )
                                            }
                                            if (chapters.any { it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal }) {
                                                DropdownMenuItem(
                                                    text = { Text("Cancel all downloads") },
                                                    onClick = { viewModel.cancelAllDownloads(); bulkMenuExpanded = false },
                                                    leadingIcon = { Icon(Icons.Default.Close, null) },
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
                                onClick = { onChapterClick(viewModel.pkg, novel.url, chapter.url) },
                                onLockedClick = { lockedDialogChapter = chapter },
                                onMarkRead = { read -> viewModel.markChapterRead(chapter, read) },
                                onMarkAllBefore = {
                                    val idx = displayedChapters.indexOf(chapter)
                                    if (idx > 0) viewModel.markChaptersRead(
                                        displayedChapters.subList(0, idx).map { it.id }, true
                                    )
                                },
                                onMarkAllAfter = {
                                    val idx = displayedChapters.indexOf(chapter)
                                    if (idx < displayedChapters.size - 1) viewModel.markChaptersRead(
                                        displayedChapters.subList(idx + 1, displayedChapters.size).map { it.id }, true
                                    )
                                },
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
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
    onMarkAllBefore: () -> Unit,
    onMarkAllAfter: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val contentAlpha = if (chapter.read || chapter.locked) 0.38f else 1f
    val dateText = remember(chapter.uploadDate) { if (chapter.uploadDate > 0L) formatDate(chapter.uploadDate) else null }
    val progressText = if (!chapter.read && chapter.readProgress > 0f) "${(chapter.readProgress * 100).toInt()}%" else null
    val subText = listOfNotNull(dateText, progressText).joinToString(" · ").takeIf { it.isNotEmpty() }
    val dlStatus = ChapterDownloadStatus.entries.getOrElse(chapter.downloadStatus) { ChapterDownloadStatus.NONE }

    Box {
        ListItem(
            headlineContent = {
                Text(
                    chapter.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
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
            trailingContent = if (chapter.locked) {
                {
                    IconButton(onClick = onLockedClick) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else when (dlStatus) {
                ChapterDownloadStatus.NONE -> ({
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
                ChapterDownloadStatus.QUEUED -> ({
                    IconButton(onClick = onCancelDownload) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
                ChapterDownloadStatus.DOWNLOADING -> ({
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        LinearProgressIndicator(modifier = Modifier.width(24.dp))
                    }
                })
                ChapterDownloadStatus.DOWNLOADED -> ({
                    IconButton(onClick = onDeleteDownload) {
                        Icon(Icons.Default.DownloadDone, contentDescription = "Delete download",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                })
                ChapterDownloadStatus.ERROR -> ({
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDeleteDownload, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                })
            },
            modifier = modifier.combinedClickable(
                onClick = if (chapter.locked) onLockedClick else onClick,
                onLongClick = { menuExpanded = true },
            ),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (chapter.read) "Mark as unread" else "Mark as read") },
                onClick = { onMarkRead(!chapter.read); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("Mark all before as read") },
                onClick = { onMarkAllBefore(); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("Mark all after as read") },
                onClick = { onMarkAllAfter(); menuExpanded = false },
            )
            if (!chapter.locked) when (dlStatus) {
                ChapterDownloadStatus.NONE, ChapterDownloadStatus.ERROR -> DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = { onDownload(); menuExpanded = false },
                    leadingIcon = { Icon(Icons.Default.Download, null) },
                )
                ChapterDownloadStatus.QUEUED -> DropdownMenuItem(
                    text = { Text("Cancel download") },
                    onClick = { onCancelDownload(); menuExpanded = false },
                )
                ChapterDownloadStatus.DOWNLOADED -> DropdownMenuItem(
                    text = { Text("Delete download") },
                    onClick = { onDeleteDownload(); menuExpanded = false },
                )
                ChapterDownloadStatus.DOWNLOADING -> {}
            }
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
