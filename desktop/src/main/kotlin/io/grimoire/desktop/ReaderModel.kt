package io.grimoire.desktop

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.source.feature.PopularSource
import io.grimoire.api.source.web.ChapterListSource
import io.grimoire.api.source.web.PageListSource
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

enum class View { LIBRARY, BROWSE, BOOK, READER }
data class ReaderState(
    val view: View = View.LIBRARY,
    val library: List<SavedBook> = emptyList(),
    val novels: List<Novel> = emptyList(),
    val book: SavedBook? = null,
    val chapter: Chapter? = null,
    val pages: List<NovelPage> = emptyList(),
    val sourceName: String? = null,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
)

/** Owns operations and source lifetime; the Compose screen only projects this state. */
class ReaderModel(
    private val library: LocalLibrary,
    private val hostApi: String,
    private val networkEnabled: AtomicBoolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closing = AtomicBoolean(false)
    private var extension: LocalExtension? = null
    private val mutable = MutableStateFlow(ReaderState(library = library.saved()))
    val state = mutable.asStateFlow()

    fun load(manifest: Path) = operate {
        val loaded = LocalExtension.open(manifest, hostApi)
        try { library.flush(); extension?.close() }
        catch (failure: Exception) { loaded.close(); throw failure }
        extension = loaded
        mutable.update { it.copy(sourceName = loaded.source.name, view = View.LIBRARY,
            novels = emptyList(), book = null, chapter = null, pages = emptyList(), library = library.saved()) }
    }

    fun browse() = operate {
        check(!state.value.offline) { "Browsing is unavailable offline" }
        val source = extension?.source as? PopularSource ?: error("Open a compatible local extension first")
        val novels = source.getPopularNovels(1)
        check(novels.isNotEmpty()) { "The source returned no novels" }
        mutable.update { it.copy(view = View.BROWSE, novels = novels) }
    }

    fun details(novel: Novel) = operate {
        check(state.value.view == View.BROWSE && novel in state.value.novels) { "This catalog selection is no longer current" }
        check(!state.value.offline) { "Novel refresh is unavailable offline" }
        val loaded = extension ?: error("Open a compatible local extension first")
        val source = loaded.source as? ChapterListSource ?: error("This source's chapter list is unsupported")
        val details = source.getNovelDetails(novel)
        val chapters = source.getChapterList(details)
        check(chapters.isNotEmpty()) { "The source returned no chapters" }
        val book = library.remember(NovelKey(loaded.sourceId, details.url), details, chapters)
        mutable.update { it.copy(view = View.BOOK, book = book, pages = emptyList(), chapter = null) }
    }

    fun showLibrary() = operate {
        library.flush()
        mutable.update { it.copy(view = View.LIBRARY, library = library.saved()) }
    }

    fun openSaved(book: SavedBook) = operate {
        library.flush()
        mutable.update { it.copy(view = View.BOOK, book = library.book(book.key), chapter = null, pages = emptyList()) }
    }

    fun favorite() = operate {
        val current = state.value.book ?: return@operate
        val book = library.favorite(current.key, !current.favorite)
        mutable.update { it.copy(book = book, library = library.saved()) }
    }

    fun read(chapter: Chapter) = operate {
        val book = state.value.book ?: error("Open a novel first")
        require(!chapter.locked && book.chapters.any { it == chapter }) { "This chapter is locked or unavailable" }
        library.flush()
        val key = ChapterKey(book.key, chapter.url)
        val pages = library.downloaded(key) ?: run {
            check(!state.value.offline) { "Download this chapter before reading offline" }
            val loaded = extension?.takeIf { it.sourceId == book.key.sourceId }
                ?: error("Open this novel's extension to read chapters that have not been downloaded")
            val source = loaded.source as? PageListSource ?: error("This source cannot provide chapter pages")
            source.getPageList(chapter)
        }
        check(pages.any { (it.content as? PageContent.Text)?.text?.isNotBlank() == true }) { "The chapter has no readable text" }
        mutable.update { it.copy(view = View.READER, book = library.book(book.key), chapter = chapter, pages = pages) }
    }

    fun download() = operate {
        val current = state.value
        val book = current.book ?: return@operate
        val chapter = current.chapter ?: return@operate
        library.download(ChapterKey(book.key, chapter.url), current.pages)
        val updated = library.favorite(book.key, true)
        mutable.update { it.copy(book = updated, library = library.saved()) }
    }

    fun offline(value: Boolean) {
        if (state.value.busy || closing.get()) return
        networkEnabled.set(!value)
        mutable.update { it.copy(offline = value, error = null) }
    }

    fun position(key: ChapterKey, paragraph: Int, offset: Int) {
        if (closing.get()) return
        val current = state.value
        val book = current.book ?: return
        val chapter = current.chapter ?: return
        if (key != ChapterKey(book.key, chapter.url)) return
        library.position(key, paragraph, offset)
    }

    suspend fun flushPosition() = withContext(Dispatchers.IO) { library.flush() }

    suspend fun close() {
        closing.set(true)
        scope.coroutineContext.job.cancelAndJoin()
        withContext(Dispatchers.IO) {
            library.flush()
            extension?.close()
            extension = null
        }
    }

    private fun operate(action: suspend () -> Unit) {
        if (state.value.busy || closing.get()) return
        // Claim busy synchronously, before dispatching, so repeated clicks cannot overlap.
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
