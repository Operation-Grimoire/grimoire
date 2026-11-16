package io.grimoire.desktop

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.source.Source
import io.grimoire.api.source.feature.PopularSource
import io.grimoire.api.source.web.ChapterListSource
import io.grimoire.api.source.web.CursorPaginatedSource
import io.grimoire.api.source.web.PageListSource
import io.grimoire.api.source.web.getAllChapters
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.ChapterLoader
import io.grimoire.reading.ChapterPageAccess
import io.grimoire.reading.ChapterLoadFailure
import io.grimoire.reading.ChapterLoadException
import io.grimoire.reading.storage.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.encodeChapterContent
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
    val projection: ReadingProjection? = null,
    val observedAnchor: ReadingAnchor? = null,
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
    private val chapterLoader: ChapterLoader,
    private val membership: LibraryRepository,
    private val downloads: DownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closing = AtomicBoolean(false)
    private val operationLock = Any()
    private var extension: LocalExtension? = null
    private val mutable = MutableStateFlow(ReaderState(library = library.saved()))
    val state = mutable.asStateFlow()

    fun load(manifest: Path) = operate {
        val loaded = LocalExtension.open(manifest, hostApi)
        try { library.flush(); extension?.close() }
        catch (failure: Exception) { loaded.close(); throw failure }
        extension = loaded
        val saved = library.saved()
        val sourceName = loaded.source.name
        publishView { it.copy(sourceName = sourceName, view = View.LIBRARY,
            novels = emptyList(), book = null, chapter = null, pages = emptyList(), library = saved,
            projection = null, observedAnchor = null) }
    }

    fun browse() = operate {
        check(!state.value.offline) { "Browsing is unavailable offline" }
        val source = extension?.source as? PopularSource ?: error("Open a compatible local extension first")
        library.flush()
        val novels = source.getPopularNovels(1)
        check(novels.isNotEmpty()) { "The source returned no novels" }
        publishView { it.copy(view = View.BROWSE, novels = novels, projection = null, observedAnchor = null) }
    }

    fun details(novel: Novel) = operate {
        check(state.value.view == View.BROWSE && novel in state.value.novels) { "This catalog selection is no longer current" }
        check(!state.value.offline) { "Novel refresh is unavailable offline" }
        val loaded = extension ?: error("Open a compatible local extension first")
        val source = loaded.source
        check(source is ChapterListSource || source is CursorPaginatedSource) { "This source's chapter list is unsupported" }
        val details = source.getNovelDetails(novel)
        val chapters = sourceChapters(source, details)
        check(chapters.isNotEmpty()) { "The source returned no chapters" }
        val book = library.remember(NovelKey(loaded.sourceId, details.url), details, chapters)
        publishView { it.copy(view = View.BOOK, book = book, pages = emptyList(), chapter = null, projection = null, observedAnchor = null) }
    }

    fun showLibrary() = operate {
        library.flush()
        val saved = library.saved()
        publishView { it.copy(view = View.LIBRARY, library = saved, projection = null, observedAnchor = null) }
    }

    fun openSaved(book: SavedBook) = operate {
        library.flush()
        val stored = library.book(book.key)
        publishView { it.copy(view = View.BOOK, book = stored, chapter = null, pages = emptyList(), projection = null, observedAnchor = null) }
    }

    fun favorite() = operate {
        val current = state.value.book ?: return@operate
        val book = setMembership(current.key, !current.favorite)
        mutable.update { it.copy(book = book, library = library.saved()) }
    }

    fun read(chapter: Chapter) = operate {
        val book = state.value.book ?: error("Open a novel first")
        require(!chapter.locked && book.chapters.any { it == chapter }) { "This chapter is locked or unavailable" }
        library.flush()
        val key = ChapterKey(book.key, chapter.url)
        val pages = try {
            chapterLoader.load(key) {
                when {
                    state.value.offline -> ChapterPageAccess.Unavailable(ChapterLoadFailure.OFFLINE)
                    else -> {
                        val loaded = extension?.takeIf { it.sourceId == book.key.sourceId }
                        val source = loaded?.source as? PageListSource
                        when {
                            loaded == null -> ChapterPageAccess.Unavailable(ChapterLoadFailure.SOURCE_UNAVAILABLE)
                            source == null -> ChapterPageAccess.Unavailable(ChapterLoadFailure.PAGES_UNSUPPORTED)
                            else -> ChapterPageAccess.Available { source.getPageList(chapter) }
                        }
                    }
                }
            }.pages
        } catch (failure: ChapterLoadException) {
            error(when (failure.reason) {
                ChapterLoadFailure.OFFLINE -> "Download this chapter before reading offline"
                ChapterLoadFailure.SOURCE_UNAVAILABLE -> "Open this novel's extension to read chapters that have not been downloaded"
                ChapterLoadFailure.PAGES_UNSUPPORTED -> "This source cannot provide chapter pages"
            })
        }
        check(pages.any { (it.content as? PageContent.Text)?.text?.isNotBlank() == true }) { "The chapter has no readable text" }
        val projection = ReadingProjection.create(key, pages, pages.mapIndexed { ordinal, page -> ProjectedPage(ordinal, page) })
        val stored = library.book(book.key)
        publishView { it.copy(view = View.READER, book = stored, chapter = chapter, pages = projection.originalPages,
            projection = projection, observedAnchor = null) }
    }

    fun download() = operate {
        val current = state.value
        val book = current.book ?: return@operate
        val chapter = current.chapter ?: return@operate
        val key = ChapterKey(book.key, chapter.url)
        val attempt = downloads.claim(key) ?: error("Chapter is unavailable or locked")
        val expected = downloadDigest(encodeChapterContent(current.pages))
        try {
            check(downloads.commit(attempt, current.pages) is DownloadCommit.Committed) { "Download attempt is no longer current" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // The commit may have succeeded even when its reply was lost. Never start a new attempt here.
            val receipt = try { downloads.currentReceipt(key) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (readFailure: Exception) { failure.addSuppressed(readFailure); throw failure }
            if (receipt?.attemptId != attempt.id || receipt.digest != expected) throw failure
        }
        mutable.update { it.copy(book = library.book(book.key), library = library.saved()) }
        try {
            val updated = setMembership(book.key, true)
            mutable.update { it.copy(book = updated, library = library.saved()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error("Chapter saved, but could not add the novel to your library: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    fun offline(value: Boolean) {
        if (state.value.busy || closing.get()) return
        networkEnabled.set(!value)
        mutable.update { it.copy(offline = value, error = null) }
    }

    private suspend fun setMembership(key: NovelKey, inLibrary: Boolean): SavedBook {
        val entry = membership.setMembership(key, inLibrary) ?: error("Novel is no longer stored")
        return library.book(entry.key) ?: error("Novel is no longer stored")
    }

    private fun publishView(change: (ReaderState) -> ReaderState) = synchronized(operationLock) {
        mutable.update(change)
    }

    fun position(key: ChapterKey, paragraph: Int, offset: Int, observed: ReadingProjection) {
        synchronized(operationLock) {
            if (closing.get() || offset < 0) return
            val current = state.value
            val book = current.book ?: return
            val chapter = current.chapter ?: return
            if (current.view != View.READER || current.projection !== observed ||
                key != ChapterKey(book.key, chapter.url) || paragraph !in 0 until observed.itemCount) return
            library.position(key, paragraph, offset)
            mutable.update { it.copy(observedAnchor = observed.capture(paragraph)) }
        }
    }

    suspend fun flushPosition() {
        val prefix = "Could not save reading position:"
        try {
            withContext(Dispatchers.IO) { library.flush() }
            mutable.update { if (it.error?.startsWith(prefix) == true) it.copy(error = null) else it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(error = "$prefix ${failure.message ?: failure.javaClass.simpleName}") }
        }
    }

    suspend fun close() {
        synchronized(operationLock) { closing.set(true) }
        try {
            // Let the claimed operation finish, then save before cancelling the usable scope.
            scope.coroutineContext.job.children.toList().joinAll()
            withContext(Dispatchers.IO) { library.close() }
        } catch (failure: Exception) {
            closing.set(false)
            throw failure
        }
        scope.coroutineContext.job.cancelAndJoin()
        withContext(Dispatchers.IO) {
            extension?.close()
            extension = null
        }
    }

    private fun operate(action: suspend () -> Unit) {
        synchronized(operationLock) {
            if (state.value.busy || closing.get()) return
            // Claim and register the job atomically with respect to other operations/close.
            mutable.update { it.copy(busy = true, error = null) }
            scope.launch {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { mutable.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) } }
                finally { mutable.update { it.copy(busy = false) } }
            }
        }
    }
}

internal suspend fun sourceChapters(source: Source, novel: Novel): List<Chapter> = when (source) {
    is CursorPaginatedSource -> source.getAllChapters(novel)
    is ChapterListSource -> source.getChapterList(novel)
    else -> error("This source's chapter list is unsupported")
}
