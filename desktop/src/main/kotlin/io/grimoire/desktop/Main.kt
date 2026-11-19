package io.grimoire.desktop

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.network.SourceNetwork
import io.grimoire.reading.ChapterKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    require(args.size % 2 == 0) { "Use --extension MANIFEST --data DIRECTORY" }
    val options = args.toList().chunked(2).associate { it[0] to it[1] }
    require(options.keys.all { it == "--extension" || it == "--data" }) { "Unknown desktop argument" }
    val data = options["--data"]?.let(Paths::get) ?: Paths.get(
        System.getenv("APPDATA") ?: System.getProperty("user.home"), "GrimoireDesktop")
    val hostApi = HostRuntime.apiVersion
    val networkEnabled = AtomicBoolean(true)
    SourceNetwork.install(DesktopHttpPlatform(networkEnabled))
    val library = LocalLibrary(data)
    val extensions = ExtensionManager(library, hostApi, HttpArtifactFetcher(networkEnabled), listOf(OFFICIAL_REPOSITORY))
    val model = desktopReaderModel(library, hostApi, networkEnabled, extensions)
    options["--extension"]?.let { model.load(Paths.get(it)) }
    application {
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        var closeError by remember { mutableStateOf<String?>(null) }
        Window(
            title = "Grimoire",
            state = rememberWindowState(width = 1150.dp, height = 820.dp),
            onKeyEvent = { event ->
                val current = model.state.value
                val back = event.key == Key.Escape && event.type == KeyEventType.KeyUp &&
                    current.view.isBookView && !current.busy && !closing
                if (back) model.back()
                back
            },
            onCloseRequest = {
                if (!closing) {
                    closing = true
                    scope.launch {
                        try { model.close(); exitApplication() }
                        catch (failure: Exception) { closeError = "Could not save the library: ${failure.message}"; closing = false }
                    }
                }
            },
        ) {
            SideEffect { window.minimumSize = java.awt.Dimension(900, 600) }
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    DesktopReader(model, closing, closeError) {
                        chooseManifest(window)?.let(model::load)
                    }
                }
            }
        }
    }
}

private fun chooseManifest(parent: Frame): Path? = FileDialog(parent, "Open local extension manifest", FileDialog.LOAD).run {
    try {
        isVisible = true
        file?.let { Paths.get(directory, it) }
    } finally { dispose() }
}

@Composable
fun DesktopReader(model: ReaderModel, closing: Boolean, closeError: String?, openExtension: () -> Unit) {
    val state by model.state.collectAsState()
    val enabled = !state.busy && !closing
    val section = if (state.view.isBookView) state.bookOrigin else state.view
    Row(Modifier.fillMaxSize()) {
        DesktopNavigation(section, enabled, model)
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (state.view != View.SETTINGS) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (state.view.isBookView) TextButton(onClick = model::back, enabled = enabled) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    OfflineToggle(state.offline, enabled, model::offline)
                }
            }
            if (state.busy || closing) LinearProgressIndicator(Modifier.fillMaxWidth())
            (closeError ?: state.error)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
            }
            when (state.view) {
                View.LIBRARY -> LibraryPane(state, enabled, model, Modifier.weight(1f))
                View.HISTORY -> HistoryPane(state, enabled, model, Modifier.weight(1f))
                View.BROWSE -> BrowsePane(state, enabled, model, Modifier.weight(1f))
                View.EXTENSIONS -> ExtensionsPane(state, enabled, model, openExtension, Modifier.weight(1f))
                View.SETTINGS -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    OfflineToggle(state.offline, enabled, model::offline)
                    Text("Offline mode disables source requests. Saved chapters stay available.")
                }
                View.BOOK, View.READER -> BookPane(state, enabled, model, Modifier.weight(1f))
            }
        }
    }
}

private val View.isBookView: Boolean get() = this == View.BOOK || this == View.READER

@Composable
private fun DesktopNavigation(section: View, enabled: Boolean, model: ReaderModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(168.dp).fillMaxHeight()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Grimoire", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
            SectionButton("Library", section == View.LIBRARY, enabled, model::showLibrary)
            SectionButton("Browse", section == View.BROWSE, enabled, model::showBrowse)
            SectionButton("History", section == View.HISTORY, enabled, model::showHistory)
            Spacer(Modifier.weight(1f))
            SectionButton("Extensions", section == View.EXTENSIONS, enabled, model::showExtensions)
            SectionButton("Settings", section == View.SETTINGS, enabled, model::showSettings)
        }
    }
}

@Composable
private fun SectionButton(label: String, active: Boolean, enabled: Boolean, navigate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = navigate,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { selected = active },
        colors = if (active) ButtonDefaults.textButtonColors(
            containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer,
        ) else ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
    ) {
        Text(label, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OfflineToggle(offline: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.toggleable(value = offline, enabled = enabled, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = offline, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(end = 8.dp))
        Text("Offline")
    }
}

@Composable
private fun BrowsePane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (state.sources.isEmpty()) "Browse sources" else "Browse", style = MaterialTheme.typography.headlineSmall)
                if (state.sources.isNotEmpty()) SourcePicker(state, enabled, model)
            }
        }
        when {
            state.sourceName == null -> item {
                Text("Install an extension to browse its catalog.")
                TextButton(onClick = model::showExtensions, enabled = enabled) { Text("Manage extensions") }
            }
            state.offline -> item { Text("Go online to browse this source.") }
            state.browseModes.isEmpty() -> item { Text("${state.sourceName} has no catalog to browse.") }
            else -> item { BrowseControls(state, enabled, model) }
        }
        if (state.sourceName != null && !state.offline && state.browseMode != null) {
            items(state.novels, key = { it.url }) { novel ->
                OutlinedButton(onClick = { model.details(novel) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(novel.title, Modifier.fillMaxWidth().padding(10.dp))
                }
            }
            item { BrowseFooter(state, enabled, model) }
        }
    }
}

@Composable
private fun BrowseControls(state: ReaderState, enabled: Boolean, model: ReaderModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.browseModes.size > 1) {
            SingleChoiceSegmentedButtonRow {
                state.browseModes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == state.browseMode,
                        onClick = { if (mode != state.browseMode) model.setBrowseMode(mode) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, state.browseModes.size),
                    ) { Text(mode.label) }
                }
            }
        }
        if (state.browseMode == BrowseMode.SEARCH) {
            // Keyed by source so a new source starts with its own empty field.
            var query by remember(state.browseSource) { mutableStateOf(state.browseQuery) }
            val submit = { if (query.isNotBlank()) model.search(query) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, enabled = enabled,
                    label = { Text("Search ${state.sourceName} by title") },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (enter && event.type == KeyEventType.KeyUp) submit()
                        enter
                    })
                Button(onClick = submit, enabled = enabled && query.isNotBlank()) { Text("Search") }
            }
        }
    }
}

@Composable
private fun BrowseFooter(state: ReaderState, enabled: Boolean, model: ReaderModel) {
    when {
        state.browseHasMore -> OutlinedButton(onClick = model::loadMore, enabled = enabled) { Text("Load more") }
        state.browsePage == 0 && state.browseMode != BrowseMode.SEARCH && enabled ->
            OutlinedButton(onClick = model::browse) { Text("Load ${state.browseMode?.label?.lowercase()} titles") }
        state.browsePage > 0 && state.novels.isEmpty() -> Text(
            if (state.browseMode == BrowseMode.SEARCH) "No results for “${state.browseQuery}”."
            else "${state.sourceName} returned no titles.")
        state.browsePage > 0 -> Text("End of results", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BookPane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier) {
    val book = state.book ?: return
    val current = state.chapter
    Column(modifier) {
        Text(book.novel.title, style = MaterialTheme.typography.headlineSmall)
        book.novel.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = model::favorite, enabled = enabled) {
                Text(if (book.favorite) "Remove from library" else "Add to library")
            }
            if (current != null) {
                TextButton(onClick = model::download, enabled = enabled) {
                    Text(if (current.url in book.downloaded) "Saved offline" else "Save chapter offline")
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.weight(1f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ChapterPane(book, current, enabled, state.offline, model, Modifier.width(240.dp).fillMaxHeight())
            if (current != null) {
                key(book.key, current.url) { ReaderPane(state, model, Modifier.weight(1f)) }
            } else {
                val description = remember(book.novel.description) { org.jsoup.Jsoup.parse(book.novel.description.orEmpty()).text() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("${book.chapters.size} chapters", style = MaterialTheme.typography.titleLarge) }
                    book.resumeChapter()?.let { chapter ->
                        item {
                            Button(onClick = { model.read(chapter) }, enabled = enabled && !chapter.locked &&
                                (!state.offline || chapter.url in book.downloaded)) { Text("Continue reading") }
                        }
                    }
                    item { Text(description, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

@Composable
private fun LibraryPane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Library", style = MaterialTheme.typography.headlineSmall) }
        if (state.library.isEmpty()) item {
            Text("Books you add from Browse appear here.")
        }
        items(state.library, key = { it.key }) { book ->
            OutlinedButton(onClick = { model.openSaved(book) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(book.novel.title, style = MaterialTheme.typography.titleLarge)
                    Text("${book.downloaded.size} chapters saved offline")
                }
            }
        }
    }
}

@Composable
private fun ChapterPane(book: SavedBook, current: Chapter?, enabled: Boolean, offline: Boolean, model: ReaderModel, modifier: Modifier = Modifier) {
    val scroll = rememberLazyListState()
    Box(modifier) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
            items(book.chapters, key = { it.url }) { chapter ->
                TextButton(onClick = { model.read(chapter) }, enabled = enabled && !chapter.locked &&
                    (!offline || chapter.url in book.downloaded), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(chapter.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val status = when {
                            chapter.locked -> "Locked"
                            chapter.url == current?.url -> "Reading"
                            chapter.url in book.downloaded -> "Saved offline"
                            offline -> "Not downloaded"
                            else -> null
                        }
                        status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxHeight().align(androidx.compose.ui.Alignment.CenterEnd))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReaderPane(state: ReaderState, model: ReaderModel, modifier: Modifier = Modifier) {
    val book = checkNotNull(state.book)
    val chapter = checkNotNull(state.chapter)
    val projection = checkNotNull(state.projection)
    val position = book.position?.takeIf { it.chapterUrl == chapter.url }
    val viewport = state.checkpointLoad?.restore?.viewport
    val initialIndex = (viewport?.index ?: position?.paragraph ?: 0).coerceIn(0, state.pages.lastIndex)
    val scroll = androidx.compose.runtime.key(projection) {
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
    val key = ChapterKey(book.key, chapter.url)
    LaunchedEffect(key, projection, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
            model.position(key, index, offset, projection, userInitiated = false)
            delay(350)
            model.flushPosition()
        }
    }
    Column(modifier) {
        Text(chapter.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        state.positionNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
        Box(Modifier.weight(1f)) {
            SelectionContainer {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("reader-body")
                    .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { if (restored) model.userPosition(projection) }
                    .nestedScroll(input).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(projection.body, key = { it.canonicalOrdinal }) { mapped ->
                        when (val content = mapped.page.content) {
                            is PageContent.Text -> Text(content.text, style = MaterialTheme.typography.bodyLarge)
                            is PageContent.Separator -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            is PageContent.Image -> Text("Image unavailable", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxHeight().align(androidx.compose.ui.Alignment.CenterEnd)
                .onPointerEvent(PointerEventType.Press) { if (restored) model.userPosition(projection) })
        }
    }
}
