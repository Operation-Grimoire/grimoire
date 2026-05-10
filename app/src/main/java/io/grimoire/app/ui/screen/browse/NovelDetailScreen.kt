package io.grimoire.app.ui.screen.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.ui.component.FastScroller
import io.grimoire.app.ui.component.ShimmerBox
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelDetailScreen(
    onNavigateBack: () -> Unit,
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

    var descriptionExpanded by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showJumpDialog by remember { mutableStateOf(false) }

    val hasUploadDates by remember(chapters) {
        derivedStateOf { chapters.any { it.uploadDate > 0L } }
    }

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

    // Number of LazyColumn items before chapter items — used for fast scroller label
    val chapterHeaderOffset by remember(isLoadingNovel, novelError, novel, chaptersError, isFavorite, categories) {
        derivedStateOf {
            var count = 1 // novel header / skeleton / error
            if (!isLoadingNovel && novelError == null) {
                if (isFavorite && categories.isNotEmpty()) count++ // category row
                if (novel.genres.isNotEmpty()) count++
                if (!novel.description.isNullOrBlank()) count++
            }
            count++ // chapter controls row
            if (chaptersError != null) count++
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
        JumpDialog(
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

    Scaffold(
        modifier = modifier,
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
        PullToRefreshBox(
            isRefreshing = isLoadingNovel && novel.initialized,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Novel header
                    item {
                        when {
                            isLoadingNovel -> NovelHeaderSkeleton()
                            novelError != null -> Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(novelError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = viewModel::retryNovel) { Text("Retry") }
                            }
                            else -> NovelHeader(novel = novel)
                        }
                    }

                    // Category (when in library)
                    if (!isLoadingNovel && novelError == null && isFavorite && categories.isNotEmpty()) {
                        item {
                            val currentCat = categories.firstOrNull { cat ->
                                if (cat.isDefault) categoryId == null else cat.id == categoryId
                            }
                            Row(
                                modifier = Modifier
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
                        item {
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                novel.genres.forEach { genre ->
                                    AssistChip(onClick = {}, label = { Text(genre) })
                                }
                            }
                        }
                    }

                    // Description
                    if (!isLoadingNovel && novelError == null && !novel.description.isNullOrBlank()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    novel.description!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = if (descriptionExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    onClick = { descriptionExpanded = !descriptionExpanded },
                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                ) {
                                    Text(if (descriptionExpanded) "Show less" else "Show more")
                                }
                            }
                        }
                    }

                    // Chapter controls
                    item {
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
                                if (isLoadingChapters) {
                                    CircularProgressIndicator(Modifier.size(18.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                                }
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
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(chaptersError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = viewModel::retryChapters) { Text("Retry") }
                            }
                        }
                    }

                    // Chapter list
                    items(displayedChapters, key = { it.url }) { chapter ->
                        ChapterItem(chapter = chapter)
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpDialog(onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to chapter") },
        text = {
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
private fun NovelHeader(novel: Novel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = novel.thumbnailUrl,
            contentDescription = novel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(novel.title, style = MaterialTheme.typography.titleLarge)
            if (!novel.author.isNullOrBlank()) {
                Text(novel.author!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(novel.status.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChapterItem(chapter: Chapter, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (chapter.uploadDate > 0L) {
            { Text(remember(chapter.uploadDate) { formatDate(chapter.uploadDate) }) }
        } else null,
        modifier = modifier.clickable { },
    )
}

private val NovelStatus.displayName: String
    get() = when (this) {
        NovelStatus.UNKNOWN -> "Unknown"
        NovelStatus.ONGOING -> "Ongoing"
        NovelStatus.COMPLETED -> "Completed"
        NovelStatus.HIATUS -> "Hiatus"
        NovelStatus.CANCELLED -> "Cancelled"
    }

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
