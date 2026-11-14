package io.grimoire.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    require(args.size % 2 == 0) { "Use --extension MANIFEST --data DIRECTORY" }
    val options = args.toList().chunked(2).associate { it[0] to it[1] }
    require(options.keys.all { it == "--extension" || it == "--data" }) { "Unknown desktop argument" }
    val data = options["--data"]?.let(Paths::get) ?: Paths.get(
        System.getenv("APPDATA") ?: System.getProperty("user.home"), "GrimoireDesktop")
    val hostApi = Properties().apply {
        checkNotNull(ReaderModel::class.java.getResourceAsStream("/host.properties")).use(::load)
    }.getProperty("api.version")
    val networkEnabled = AtomicBoolean(true)
    SourceNetwork.install(DesktopHttpPlatform(networkEnabled))
    val model = ReaderModel(LocalLibrary(data), hostApi, networkEnabled)
    options["--extension"]?.let { model.load(Paths.get(it)) }
    application {
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        var closeError by remember { mutableStateOf<String?>(null) }
        Window(
            title = "Grimoire",
            state = rememberWindowState(width = 1150.dp, height = 820.dp),
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
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Grimoire", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = model::showLibrary, enabled = enabled) { Text("Library") }
            TextButton(onClick = model::browse, enabled = enabled && !state.offline && state.sourceName != null) { Text("Browse") }
            Spacer(Modifier.weight(1f))
            Row(Modifier.toggleable(value = state.offline, enabled = enabled, role = Role.Checkbox, onValueChange = model::offline)) {
                Checkbox(checked = state.offline, onCheckedChange = null, enabled = enabled)
                Text("Offline", modifier = Modifier.padding(top = 12.dp))
            }
            TextButton(onClick = openExtension, enabled = enabled) { Text("Open extension") }
        }
        Text(state.sourceName ?: "No extension open", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        if (state.busy || closing) LinearProgressIndicator(Modifier.fillMaxWidth())
        (closeError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
        }
        when (state.view) {
            View.LIBRARY -> LibraryPane(state, enabled, model, Modifier.weight(1f))
            View.BROWSE -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Popular", style = MaterialTheme.typography.headlineSmall) }
                items(state.novels, key = { it.url }) { novel ->
                    OutlinedButton(onClick = { model.details(novel) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(novel.title, Modifier.fillMaxWidth().padding(10.dp))
                    }
                }
            }
            View.BOOK, View.READER -> state.book?.let { book ->
                Text(book.novel.title, style = MaterialTheme.typography.headlineSmall)
                book.novel.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = model::favorite, enabled = enabled) { Text(if (book.favorite) "Remove from library" else "Add to library") }
                    if (state.view == View.READER) {
                        TextButton(onClick = model::download, enabled = enabled) {
                            Text(if (state.chapter?.url in book.downloaded) "Saved offline" else "Save chapter offline")
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.weight(1f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ChapterPane(book, state.chapter, enabled, model, Modifier.width(300.dp).fillMaxHeight())
                    if (state.view == View.READER && state.chapter != null) {
                        key(book.key, state.chapter!!.url) { ReaderPane(state, model, Modifier.weight(1f)) }
                    } else {
                        val description = remember(book.novel.description) { org.jsoup.Jsoup.parse(book.novel.description.orEmpty()).text() }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Text("${book.chapters.size} chapters", style = MaterialTheme.typography.titleLarge) }
                            book.position?.let { position -> book.chapters.find { it.url == position.chapterUrl }?.let { chapter ->
                                item { Button(onClick = { model.read(chapter) }, enabled = enabled && !chapter.locked) { Text("Continue reading") } }
                            } }
                            item { Text(description, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
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
            Text("Open a local extension to browse, then add a novel to your library. Saved chapters are available offline.")
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
private fun ChapterPane(book: SavedBook, current: Chapter?, enabled: Boolean, model: ReaderModel, modifier: Modifier = Modifier) {
    val scroll = rememberLazyListState()
    Box(modifier) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
            items(book.chapters, key = { it.url }) { chapter ->
                TextButton(onClick = { model.read(chapter) }, enabled = enabled && !chapter.locked, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(chapter.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val status = when {
                            chapter.locked -> "Locked"
                            chapter.url == current?.url -> "Reading"
                            chapter.url in book.downloaded -> "Saved offline"
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

@Composable
private fun ReaderPane(state: ReaderState, model: ReaderModel, modifier: Modifier = Modifier) {
    val book = checkNotNull(state.book)
    val chapter = checkNotNull(state.chapter)
    val position = book.position?.takeIf { it.chapterUrl == chapter.url }
    val initialIndex = (position?.paragraph ?: 0).coerceIn(0, state.pages.lastIndex)
    val scroll = rememberLazyListState(initialIndex, if (initialIndex == position?.paragraph) position.offset else 0)
    val key = ChapterKey(book.key, chapter.url)
    LaunchedEffect(key) {
        snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
            model.position(key, index, offset)
            delay(350)
            model.flushPosition()
        }
    }
    Column(modifier) {
        Text(chapter.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        Box(Modifier.weight(1f)) {
            SelectionContainer {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(state.pages, key = { index, _ -> index }) { _, page ->
                        when (val content = page.content) {
                            is PageContent.Text -> Text(content.text, style = MaterialTheme.typography.bodyLarge)
                            is PageContent.Separator -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            is PageContent.Image -> Text("Image unavailable", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxHeight().align(androidx.compose.ui.Alignment.CenterEnd))
        }
    }
}
