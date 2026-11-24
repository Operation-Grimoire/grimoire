package io.grimoire.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Download
import io.grimoire.ui.icon.DownloadDone
import io.grimoire.ui.reader.*
import io.grimoire.ui.theme.isDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

private val RTL_LANGUAGES = setOf(Language.AR, Language.HE, Language.FA, Language.UR)

/** Full-window reader built from the Android reader's shared components. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(state: ReaderState, enabled: Boolean, busy: Boolean, model: ReaderModel) {
    val book = checkNotNull(state.book)
    val chapter = checkNotNull(state.chapter)
    val projection = checkNotNull(state.projection)
    val settings = state.readerSettings
    val colors = resolveReaderColorTheme(settings.colorTheme, state.appearance.themeMode.isDark()).readerColors
    val layout = readerTextLayout(settings.textAlign, book.novel.language in RTL_LANGUAGES)
    val textStyle = readerTextStyle(settings.fontSize, settings.lineHeightTimes10, settings.font, colors, layout)
    val previous = adjacentChapter(book.chapters, chapter, -1)
    val next = adjacentChapter(book.chapters, chapter, 1)
    val saved = chapter.url in book.downloaded
    var barsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showGrimoirePopup by remember { mutableStateOf(false) }

    // Position capture and restore: unchanged from the previous desktop reader.
    val position = book.position?.takeIf { it.chapterUrl == chapter.url }
    val viewport = state.checkpointLoad?.restore?.viewport
    val initialIndex = (viewport?.index ?: position?.paragraph ?: 0).coerceIn(0, state.pages.lastIndex)
    val scroll = key(projection) {
        rememberLazyListState(initialIndex, viewport?.offset ?: if (initialIndex == position?.paragraph) position.offset else 0)
    }
    var restored by remember(projection) { mutableStateOf(false) }
    LaunchedEffect(projection) {
        snapshotFlow { scroll.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        if (viewport?.end == true) {
            scroll.scrollToItem(projection.itemCount - 1)
            val info = snapshotFlow { scroll.layoutInfo }.first { it.visibleItemsInfo.any { item -> item.index == projection.itemCount - 1 } }
            val last = info.visibleItemsInfo.last()
            scroll.scrollBy((last.offset + last.size - info.viewportEndOffset).coerceAtLeast(0).toFloat())
        }
        restored = true
    }
    val input = remember(projection) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (restored && source == NestedScrollSource.UserInput && available != Offset.Zero) model.userPosition(projection)
            return Offset.Zero
        }
    } }
    val chapterKey = ChapterKey(book.key, chapter.url)
    LaunchedEffect(chapterKey, projection, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
            model.position(chapterKey, index, offset, projection, userInitiated = false)
            delay(350)
            model.flushPosition()
        }
    }

    // Progress like Android: the share of the chapter scrolled past, and the place in the book.
    val chapterFraction by remember(projection) {
        derivedStateOf {
            val info = scroll.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null || info.totalItemsCount == 0) 0f else (last.index + 1f) / info.totalItemsCount
        }
    }
    val chapterIndex = book.chapters.indexOfFirst { it.url == chapter.url }.coerceAtLeast(0)
    val progressText = readerProgressText(
        chapterPercent = if (settings.showChapterPercent) (chapterFraction * 100).toInt() else null,
        bookPercent = if (settings.showBookPercent) ((chapterIndex + chapterFraction) * 100 / book.chapters.size.coerceAtLeast(1)).toInt() else null,
    )
    val wave = remember { GrimoireWaveClock() }
    GrimoireWaveTicker(wave, settings.grimoireEasterEgg)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ReaderTopBar(
            visible = barsVisible,
            title = chapter.name,
            colors = colors,
            onBack = { if (enabled) model.back() },
            progressText = progressText,
        ) {
            val label = if (saved) "Saved offline" else "Save chapter offline"
            PlainTooltipIconButton(onClick = model::download, tooltip = label, enabled = enabled && !saved) {
                Icon(if (saved) AppIcons.DownloadDone else AppIcons.Download, contentDescription = label)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        state.positionNotice?.let { ReaderPositionNotice(it, colors) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer {
                LazyColumn(
                    state = scroll,
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxHeight().widthIn(max = 820.dp).align(Alignment.TopCenter).testTag("reader-body")
                        .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { if (restored) model.userPosition(projection) }
                        .nestedScroll(input),
                ) {
                    // List indexes are projection indexes (saved positions use them), so the title and the
                    // chapter end are drawn inside the first and last rows instead of rows of their own.
                    itemsIndexed(projection.body, key = { _, mapped -> mapped.canonicalOrdinal }) { index, mapped ->
                        Column {
                        if (index == 0) ReaderChapterTitle(chapter.name, colors, settings.font.fontFamily, layout)
                        when (val content = mapped.page.content) {
                            is PageContent.Text -> ReaderParagraph(
                                formattedText = content.html,
                                plainText = content.text,
                                highlighted = false,
                                textStyle = textStyle,
                                foreground = colors.foreground,
                                paragraphSpacing = settings.paragraphSpacing,
                                effectActive = settings.grimoireEasterEgg,
                                wave = wave,
                                onWordTap = { showGrimoirePopup = true },
                                onOtherTap = { barsVisible = !barsVisible },
                            )
                            is PageContent.Separator -> ReaderSeparator(colors, settings.paragraphSpacing)
                            is PageContent.Image -> Text(
                                "Image unavailable", style = MaterialTheme.typography.labelMedium,
                                color = colors.foreground.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = settings.paragraphSpacing.dp),
                            )
                        }
                        if (index == projection.body.lastIndex) ReaderChapterEnd(next != null, colors) { if (enabled) model.readNext() }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxHeight().align(Alignment.CenterEnd)
                .onPointerEvent(PointerEventType.Press) { if (restored) model.userPosition(projection) })
        }
        ReaderBottomBar(
            visible = barsVisible,
            colors = colors,
            hasPrevious = enabled && previous != null,
            hasNext = enabled && next != null,
            onPrevious = model::readPrevious,
            onSettings = { showSettings = true },
            onNext = model::readNext,
        )
    }

    if (showGrimoirePopup) {
        GrimoireEasterEggDialog(
            enabled = settings.grimoireEasterEgg,
            onToggle = { model.setReaderSettings(settings.copy(grimoireEasterEgg = it)) },
            onDismiss = { showGrimoirePopup = false },
        )
    }
    if (showSettings) {
        val update = { change: DesktopReaderSettings -> model.setReaderSettings(change) }
        ReaderSettingsSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            colors = colors,
            textStyle = textStyle,
            previewText = remember(projection) { previewText(projection.body.mapNotNull { (it.page.content as? PageContent.Text)?.text }) },
            fontSize = settings.fontSize,
            lineHeightTimes10 = settings.lineHeightTimes10,
            paragraphSpacing = settings.paragraphSpacing,
            readerFont = settings.font,
            colorTheme = resolveReaderColorTheme(settings.colorTheme, state.appearance.themeMode.isDark()),
            orientation = null,
            textAlign = settings.textAlign,
            hideInlineImages = null,
            showChapterProgressPercent = settings.showChapterPercent,
            showNovelProgressPercent = settings.showBookPercent,
            grimoireEasterEggEnabled = settings.grimoireEasterEgg,
            markAsReadStrategy = null,
            markAsReadThreshold = 0,
            markAsReadParagraphsFromEnd = 0,
            onDismiss = { showSettings = false },
            onFontSize = { update(settings.copy(fontSize = it)) },
            onLineHeight = { update(settings.copy(lineHeightTimes10 = it)) },
            onParagraphSpacing = { update(settings.copy(paragraphSpacing = it)) },
            onFont = { update(settings.copy(font = it)) },
            onColorTheme = { update(settings.copy(colorTheme = it)) },
            onOrientation = {},
            onTextAlign = { update(settings.copy(textAlign = it)) },
            onHideInlineImages = {},
            onShowChapterProgressPercent = { update(settings.copy(showChapterPercent = it)) },
            onShowNovelProgressPercent = { update(settings.copy(showBookPercent = it)) },
            onGrimoireEasterEggEnabled = { update(settings.copy(grimoireEasterEgg = it)) },
            onMarkAsReadStrategy = {},
            onMarkAsReadThreshold = {},
            onMarkAsReadParagraphsFromEnd = {},
        )
    }
}

/** A short sample of the chapter for the settings preview, like Android's. */
internal fun previewText(paragraphs: List<String>): String = buildString {
    for (text in paragraphs) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) continue
        if (isNotEmpty()) append("\n")
        append(trimmed)
        if (length >= 160) break
    }
}.take(280).ifBlank { "The quick brown fox jumps over the lazy dog." }
