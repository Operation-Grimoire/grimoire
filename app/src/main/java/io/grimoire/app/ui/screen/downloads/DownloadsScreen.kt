package io.grimoire.app.ui.screen.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val downloads by viewModel.downloads.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    var collapsedNovels by remember { mutableStateOf(setOf<Long>()) }
    var statusFilter by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = viewModel::togglePause) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No downloads", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val chips = listOf(
                            "All" to null,
                            "Downloading" to ChapterDownloadStatus.DOWNLOADING.ordinal,
                            "Queued" to ChapterDownloadStatus.QUEUED.ordinal,
                            "Done" to ChapterDownloadStatus.DOWNLOADED.ordinal,
                            "Failed" to ChapterDownloadStatus.ERROR.ordinal,
                        )
                        items(chips) { (label, value) ->
                            FilterChip(
                                selected = statusFilter == value,
                                onClick = { statusFilter = value },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                downloads.forEach { novelDownloads ->
                    val filtered = if (statusFilter == null) novelDownloads.chapters
                        else novelDownloads.chapters.filter { it.downloadStatus == statusFilter }
                    if (filtered.isEmpty()) return@forEach

                    val novelId = novelDownloads.novel.id
                    val isCollapsed = novelId in collapsedNovels

                    item(key = "header_$novelId") {
                        NovelDownloadHeader(
                            novelDownloads = novelDownloads,
                            collapsed = isCollapsed,
                            onToggleCollapse = {
                                collapsedNovels = if (isCollapsed)
                                    collapsedNovels - novelId
                                else
                                    collapsedNovels + novelId
                            },
                            onMoveToTop = { viewModel.moveToTopOfQueue(novelId) },
                            onCancelAll = { viewModel.cancelAll(novelId) },
                            onDeleteAll = { viewModel.deleteAllDownloads(novelId) },
                        )
                    }

                    if (!isCollapsed) {
                        items(items = filtered, key = { it.id }) { chapter ->
                            ChapterDownloadItem(
                                chapter = chapter,
                                onCancel = { viewModel.cancel(chapter) },
                                onDelete = { viewModel.deleteDownload(chapter) },
                            )
                        }
                    }

                    item(key = "divider_$novelId") {
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelDownloadHeader(
    novelDownloads: NovelDownloads,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onMoveToTop: () -> Unit,
    onCancelAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val downloaded = novelDownloads.chapters.count { it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal }
    val queued = novelDownloads.chapters.count { it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal }
    val downloading = novelDownloads.chapters.count { it.downloadStatus == ChapterDownloadStatus.DOWNLOADING.ordinal }
    val error = novelDownloads.chapters.count { it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal }

    val stats = buildList {
        if (downloaded > 0) add("$downloaded downloaded")
        if (queued > 0) add("$queued queued")
        if (downloading > 0) add("$downloading downloading")
        if (error > 0) add("$error failed")
    }.joinToString(" • ")

    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onToggleCollapse,
                    onLongClick = { showMenu = true },
                )
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = novelDownloads.novel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = novelDownloads.novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (queued > 0) {
                DropdownMenuItem(
                    text = { Text("Move to top of queue") },
                    onClick = { onMoveToTop(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.KeyboardDoubleArrowUp, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Cancel all queued") },
                    onClick = { onCancelAll(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                )
            }
            if (downloaded > 0) {
                DropdownMenuItem(
                    text = { Text("Delete all downloaded") },
                    onClick = { onDeleteAll(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun ChapterDownloadItem(
    chapter: ChapterEntity,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = ChapterDownloadStatus.entries.getOrElse(chapter.downloadStatus) { ChapterDownloadStatus.NONE }

    ListItem(
        headlineContent = {
            Text(chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            when (status) {
                ChapterDownloadStatus.QUEUED -> Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = "Queued",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                ChapterDownloadStatus.DOWNLOADED -> Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.ERROR -> Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                ChapterDownloadStatus.NONE -> Spacer(modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = {
            when (status) {
                ChapterDownloadStatus.QUEUED -> IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                ChapterDownloadStatus.DOWNLOADED, ChapterDownloadStatus.ERROR -> IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
                else -> Spacer(modifier = Modifier.width(48.dp))
            }
        },
    )
}
