package io.grimoire.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.ChapterRow
import io.grimoire.ui.component.ExpandableText
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.ArrowBack
import io.grimoire.ui.icon.DownloadDone
import io.grimoire.ui.icon.FileDownloadOff
import io.grimoire.ui.icon.Lock
import io.grimoire.ui.icon.PlayArrow
import io.grimoire.ui.icon.SwapVert
import io.grimoire.ui.novel.MetaChip
import io.grimoire.ui.novel.NovelActionRow
import io.grimoire.ui.novel.NovelHeaderContent
import io.grimoire.ui.theme.premiumGold

/** Book details built from the Android novel details components. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BookScreen(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier) {
    val book = state.book ?: return
    val sourceName = state.sources.firstOrNull { it.sourceId == book.key.sourceId }?.name.orEmpty()
    val resume = book.resumeChapter()
    val ordered = remember(book.chapters) { readingOrder(book.chapters) }
    var newestFirst by remember(book.key) { mutableStateOf(false) }
    val displayedChapters = remember(ordered, newestFirst) { if (newestFirst) ordered.asReversed() else ordered }
    val scroll = rememberLazyListState()
    val showBarTitle by remember { derivedStateOf { scroll.firstVisibleItemIndex > 0 } }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = scroll,
            contentPadding = PaddingValues(top = DESKTOP_HEADER_HEIGHT + 4.dp, bottom = 24.dp),
            modifier = Modifier.widthIn(max = 960.dp).fillMaxHeight().align(Alignment.TopCenter),
        ) {
            item {
                NovelHeaderContent(
                    novel = book.novel,
                    cover = { BookCover(book.novel.thumbnailUrl, book.novel.title, it) },
                    sourceName = sourceName,
                    sourceMissing = sourceName.isEmpty(),
                )
            }
            item {
                NovelActionRow(
                    inLibrary = book.favorite,
                    onToggleLibrary = { if (enabled) model.favorite() },
                    showWebView = false,
                    onOpenWebView = {},
                    categoryName = null,
                    onEditCategory = {},
                    userRating = null,
                    onRate = {},
                    showCategory = false,
                    showRating = false,
                )
            }
            // Continue where the reader left off, or start at the first readable chapter in reading order.
            val target = resume ?: ordered.firstOrNull { !it.locked }
            if (target != null) item {
                Button(
                    onClick = { model.read(target) },
                    enabled = enabled && !target.locked && (!state.offline || target.url in book.downloaded),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (resume != null) "Continue reading" else "Start reading")
                }
            }
            book.novel.description?.takeIf { it.isNotBlank() }?.let { description ->
                item { ExpandableText(description, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            if (book.novel.genres.isNotEmpty()) item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    book.novel.genres.forEach { MetaChip(it) }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                ) {
                    Text("${book.chapters.size} chapters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (book.chapters.size > 1) {
                        val sortTooltip = "Reverse chapter order"
                        PlainTooltipIconButton(onClick = { newestFirst = !newestFirst }, tooltip = sortTooltip) {
                            Icon(AppIcons.SwapVert, contentDescription = sortTooltip)
                        }
                    }
                }
            }
            items(displayedChapters, key = { it.url }) { chapter ->
                val saved = chapter.url in book.downloaded
                ChapterRow(
                    name = chapter.name,
                    uploadDate = chapter.uploadDate,
                    read = false,
                    readProgress = 0f,
                    locked = chapter.locked,
                    selected = false,
                    enabled = enabled && !chapter.locked && (!state.offline || saved),
                    onClick = { model.read(chapter) },
                    modifier = Modifier.animateItem(),
                    trailing = {
                        when {
                            chapter.locked -> Icon(AppIcons.Lock, "Locked", tint = MaterialTheme.colorScheme.premiumGold)
                            saved -> Icon(AppIcons.DownloadDone, "Saved offline", tint = MaterialTheme.colorScheme.primary)
                            state.offline -> Icon(AppIcons.FileDownloadOff, "Not downloaded",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
        }
        VerticalScrollbar(
            rememberScrollbarAdapter(scroll),
            Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(top = DESKTOP_HEADER_HEIGHT),
        )
        DesktopHeaderBar(
            navigationIcon = {
                DesktopIconCircle(icon = AppIcons.ArrowBack, tooltip = "Back", onClick = model::back, enabled = enabled)
            },
            title = {
                AnimatedVisibility(visible = showBarTitle, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                    Text(book.novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
