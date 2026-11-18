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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

enum class View { LIBRARY, BROWSE, BOOK, READER, HISTORY }
data class ReaderState(
    val view: View = View.LIBRARY,
    val library: List<SavedBook> = emptyList(),
    val novels: List<Novel> = emptyList(),
    val book: SavedBook? = null,
    val chapter: Chapter? = null,
    val pages: List<NovelPage> = emptyList(),
    val projection: ReadingProjection? = null,
    val observedAnchor: ReadingAnchor? = null,
    val storageObservation: ChapterStorageObservation? = null,
    val checkpointLoad: ReaderCheckpointLoad? = null,
    val positionNotice: String? = null,
    val sourceName: String? = null,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val historyRecording: Boolean = false,
    val history: List<DesktopHistoryEntry> = emptyList(),
)

/** Owns operations and source lifetime; the Compose screen only projects this state. */
class ReaderModel(
    private val library: LocalLibrary,
    private val hostApi: String,
    private val networkEnabled: AtomicBoolean,
    private val chapterLoader: ChapterLoader,
    private val membership: LibraryRepository,
    private val downloads: DownloadRepository,
    checkpointDao: ReadingCheckpointDao = DesktopReadingCheckpointDao(library),
    operationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + operationDispatcher)
    private val checkpoints = ReaderCheckpoints(checkpointDao)
    private val closing = AtomicBoolean(false)
    private val storageClosed = AtomicBoolean(false)
    private val closeWrites = Mutex()
    private val operationLock = Any()
    private var extension: LocalExtension? = null
    private val mutable = MutableStateFlow(ReaderState(library = library.saved(), historyRecording = library.historyRecording()))
    // The history worker is drained separately from navigation jobs before closing the store.
    private val historyScope = CoroutineScope(SupervisorJob() + operationDispatcher)
    private val historyWrites = OpenedHistoryWrites(historyScope, DesktopOpenedHistoryDao(library)) { failure ->
        System.getLogger("OpenedHistory").log(System.Logger.Level.WARNING, "Reading history action failed (${failure.javaClass.simpleName})")
    }
    private var lastHistoryOpen: Deferred<Result<Unit>>? = null
    val state = mutable.asStateFlow()

    fun load(manifest: Path) = navigate {
        val loaded = LocalExtension.open(manifest, hostApi)
        try { extension?.close() }
        catch (failure: Exception) { loaded.close(); throw failure }
        extension = loaded
        val saved = library.saved()
        val sourceName = loaded.source.name
        publishView { it.copy(sourceName = sourceName, view = View.LIBRARY,
            novels = emptyList(), book = null, chapter = null, pages = emptyList(), library = saved,
            projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun browse() = navigate {
        check(!state.value.offline) { "Browsing is unavailable offline" }
        val source = extension?.source as? PopularSource ?: error("Open a compatible local extension first")
        val novels = source.getPopularNovels(1)
        check(novels.isNotEmpty()) { "The source returned no novels" }
        publishView { it.copy(view = View.BROWSE, novels = novels, projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun details(novel: Novel) = navigate {
        check(state.value.view == View.BROWSE && novel in state.value.novels) { "This catalog selection is no longer current" }
        check(!state.value.offline) { "Novel refresh is unavailable offline" }
        val loaded = extension ?: error("Open a compatible local extension first")
        val source = loaded.source
        check(source is ChapterListSource || source is CursorPaginatedSource) { "This source's chapter list is unsupported" }
        val details = source.getNovelDetails(novel)
        val chapters = sourceChapters(source, details)
        check(chapters.isNotEmpty()) { "The source returned no chapters" }
        val book = library.remember(NovelKey(loaded.sourceId, details.url), details, chapters)
        publishView { it.copy(view = View.BOOK, book = book, pages = emptyList(), chapter = null, projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun showLibrary() = navigate {
        val saved = library.saved()
        publishView { it.copy(view = View.LIBRARY, library = saved, projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun showHistory() = navigate {
        lastHistoryOpen?.join()
        val entries = library.openedHistory()
        publishView { it.copy(view = View.HISTORY, history = entries, book = null, chapter = null, pages = emptyList(),
            projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun setHistoryRecording(enabled: Boolean) = operate {
        try {
            library.setHistoryRecording(enabled)
        } finally {
            val recording = library.historyRecording()
            mutable.update { it.copy(historyRecording = recording) }
        }
    }

    fun removeHistory(id: Long) = changeHistory { historyWrites.delete(listOf(id))!!.await().getOrThrow() }

    fun clearHistory() = changeHistory { historyWrites.clear().await().getOrThrow() }

    private fun changeHistory(change: suspend () -> Unit) = operate {
        if (state.value.view != View.HISTORY) return@operate
        try {
            change()
        } finally {
            val entries = library.openedHistory()
            mutable.update { it.copy(history = entries) }
        }
    }

    fun openSaved(book: SavedBook) = navigate {
        val stored = library.book(book.key)
        publishView { it.copy(view = View.BOOK, book = stored, chapter = null, pages = emptyList(), projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun favorite() = operate {
        val current = state.value.book ?: return@operate
        val book = setMembership(current.key, !current.favorite)
        mutable.update { it.copy(book = book, library = library.saved()) }
    }

    fun read(chapter: Chapter) = navigate {
        val book = state.value.book ?: error("Open a novel first")
        require(!chapter.locked && book.chapters.any { it == chapter }) { "This chapter is locked or unavailable" }
        readChapter(book, chapter)
    }

    fun readHistory(entry: DesktopHistoryEntry) = navigate {
        require(state.value.view == View.HISTORY && entry in state.value.history) { "This history entry is no longer current" }
        val key = entry.chapter.key
        val book = library.book(key.novel) ?: error("This novel is no longer available")
        val chapter = book.chapters.find { it.url == key.url && !it.locked }
            ?: error("This chapter is locked or unavailable")
        readChapter(book, chapter)
    }

    private suspend fun readChapter(book: SavedBook, chapter: Chapter) {
        val key = ChapterKey(book.key, chapter.url)
        historyWrites.open(incognito = !state.value.historyRecording, openedAtMillis = System.currentTimeMillis()) {
            DesktopOpenedChapter(key, book.novel.title, chapter.name)
        }?.let { lastHistoryOpen = it }
        // Retire the old view before storage/source suspension can admit stale callbacks.
        publishView { it.copy(view = View.BOOK, book = book, chapter = null, pages = emptyList(), projection = null,
            observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
        val loadedChapter = try {
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
            }
        } catch (failure: ChapterLoadException) {
            error(when (failure.reason) {
                ChapterLoadFailure.OFFLINE -> "Download this chapter before reading offline"
                ChapterLoadFailure.SOURCE_UNAVAILABLE -> "Open this novel's extension to read chapters that have not been downloaded"
                ChapterLoadFailure.PAGES_UNSUPPORTED -> "This source cannot provide chapter pages"
            })
        }
        publishChapter(book, chapter, loadedChapter)
    }

    private suspend fun publishChapter(book: SavedBook, chapter: Chapter, loaded: io.grimoire.reading.LoadedChapter) {
        val key = ChapterKey(book.key, chapter.url)
        val pages = loaded.pages
        check(pages.any { (it.content as? PageContent.Text)?.text?.isNotBlank() == true }) { "The chapter has no readable text" }
        val projection = ReadingProjection.create(key, pages, pages.mapIndexed { ordinal, page -> ProjectedPage(ordinal, page) })
        val legacyBook = library.book(book.key)
        val legacy = legacyBook?.position?.takeIf { it.chapterUrl == chapter.url }?.let { LegacyReaderViewport(it.paragraph, it.offset) }
        val checkpoint = checkpoints.open(projection, loaded.storageObservation, legacy)
        val stored = library.selectResumeChapter(key)
        publishView { it.copy(view = View.READER, book = stored, chapter = chapter, pages = projection.originalPages,
            projection = projection, observedAnchor = null, storageObservation = loaded.storageObservation,
            checkpointLoad = checkpoint, positionNotice = checkpoint.restore.notice?.message) }
    }

    fun download() = operate(retireReader = true) {
        val current = state.value
        val book = current.book ?: return@operate
        val chapter = current.chapter ?: return@operate
        val previous = current.checkpointLoad
        val key = ChapterKey(book.key, chapter.url)
        var committed = false
        try {
            // Persist already captured source positions before our save rotates the generation.
            flushStoredPositions()
            val attempt = downloads.claim(key) ?: error("Chapter is unavailable or locked")
            val expected = downloadDigest(encodeChapterContent(current.pages))
            try {
                check(downloads.commit(attempt, current.pages) is DownloadCommit.Committed) { "Download attempt is no longer current" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val receipt = try { downloads.currentReceipt(key) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (readFailure: Exception) { failure.addSuppressed(readFailure); throw failure }
                if (receipt?.attemptId != attempt.id || receipt.digest != expected) throw failure
            }
            committed = true
            mutable.update { it.copy(book = library.book(book.key), library = library.saved()) }
            var membershipFailure: Exception? = null
            try {
                val updated = setMembership(book.key, true)
                mutable.update { it.copy(book = updated, library = library.saved()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                membershipFailure = failure
            }
            try {
                // Fresh saved read/registration, never authority inferred from equal logical text.
                val saved = chapterLoader.load(key) { ChapterPageAccess.Unavailable(ChapterLoadFailure.SOURCE_UNAVAILABLE) }
                publishChapter(book, chapter, saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                membershipFailure?.let(failure::addSuppressed)
                throw IllegalStateException("Chapter saved, but could not reload its reading position: ${failure.message}", failure)
            }
            membershipFailure?.let { throw IllegalStateException("Chapter saved, but could not add the novel to your library: ${it.message}", it) }
        } catch (failure: Exception) {
            if (previous != null && state.value.checkpointLoad === previous) {
                if (!committed) checkpoints.resume(previous)
                else { checkpoints.revoke(previous); mutable.update { it.copy(positionNotice = CheckpointNotice.RELOAD.message) } }
            }
            throw failure
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

    fun position(key: ChapterKey, paragraph: Int, offset: Int, observed: ReadingProjection, userInitiated: Boolean = true) {
        synchronized(operationLock) {
            if (closing.get() || offset < 0) return
            val current = state.value
            val book = current.book ?: return
            val chapter = current.chapter ?: return
            if (current.view != View.READER || current.projection !== observed ||
                key != ChapterKey(book.key, chapter.url) || paragraph !in 0 until observed.itemCount) return
            val checkpoint = current.checkpointLoad ?: return
            if (!checkpoints.canObserve(checkpoint)) return
            if (userInitiated) checkpoints.userPosition(checkpoint)
            if (!checkpoints.canRecordPosition(checkpoint)) return
            library.position(key, paragraph, offset, checkpoint)
            val captured = checkpoints.capture(checkpoint, paragraph)
            mutable.update { it.copy(observedAnchor = observed.capture(paragraph), positionNotice = if (captured != null) null else it.positionNotice) }
        }
    }

    suspend fun flushPosition() {
        val prefix = "Could not save reading position:"
        try {
            withContext(Dispatchers.IO) { flushStoredPositions() }
            mutable.update { if (it.error?.startsWith(prefix) == true) it.copy(error = null) else it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(error = "$prefix ${failure.message ?: failure.javaClass.simpleName}") }
        }
    }

    suspend fun close() = closeWrites.withLock {
        synchronized(operationLock) { closing.set(true); checkpoints.retire() }
        try {
            // Let the claimed operation finish, then save before cancelling the usable scope.
            scope.coroutineContext.job.children.toList().joinAll()
            lastHistoryOpen?.join()
            withContext(Dispatchers.IO) {
                if (!storageClosed.get()) {
                    checkpoints.retire(); flushStoredPositions(); library.close(); storageClosed.set(true)
                }
            }
        } catch (failure: Exception) {
            synchronized(operationLock) { closing.set(false); state.value.checkpointLoad?.let(checkpoints::resume) }
            throw failure
        }
        scope.coroutineContext.job.cancelAndJoin()
        historyScope.coroutineContext.job.cancelAndJoin()
        withContext(Dispatchers.IO) {
            extension?.close()
            extension = null
        }
    }

    fun userPosition(observed: ReadingProjection) = synchronized(operationLock) {
        if (!closing.get() && state.value.projection === observed) state.value.checkpointLoad?.let(checkpoints::userPosition)
    }

    private fun applyRevocations(loads: List<ReaderCheckpointLoad>) {
        loads.forEach { load -> load.projection.document?.key?.let { library.discardPendingPosition(it, load) } }
        val current = state.value.checkpointLoad
        if (current != null && checkpoints.isRevoked(current)) mutable.update {
            if (it.checkpointLoad === current) it.copy(positionNotice = CheckpointNotice.RELOAD.message) else it
        }
    }

    private suspend fun flushStoredPositions() {
        try { applyRevocations(checkpoints.flush()) }
        catch (rejected: ReaderCheckpointRejected) { applyRevocations(rejected.revokedLoads); throw rejected }
        library.flush()
    }

    private fun navigate(action: suspend () -> Unit) = operate(retireReader = true) {
        flushStoredPositions(); action()
    }

    private fun resumeClaimedReader(previous: ReaderCheckpointLoad?) = synchronized(operationLock) {
        if (!closing.get() && previous != null && state.value.checkpointLoad === previous) checkpoints.resume(previous)
    }

    private fun operate(retireReader: Boolean = false, action: suspend () -> Unit) {
        synchronized(operationLock) {
            if (state.value.busy || closing.get()) return
            val previous = if (retireReader) state.value.checkpointLoad else null
            if (retireReader) checkpoints.retire()
            // Claim/retirement/job registration are atomic with callbacks and close.
            mutable.update { it.copy(busy = true, error = null) }
            scope.launch {
                try { action() }
                catch (cancelled: CancellationException) { resumeClaimedReader(previous); throw cancelled }
                catch (failure: Exception) {
                    resumeClaimedReader(previous)
                    mutable.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) }
                }
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
