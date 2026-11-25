package io.grimoire.desktop

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.source.Source
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
import io.grimoire.ui.theme.ColorTheme
import io.grimoire.ui.theme.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class View { LIBRARY, BROWSE, BOOK, READER, HISTORY, EXTENSIONS, SETTINGS }
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
    val bookOrigin: View = View.LIBRARY,
    val sources: List<BrowseSource> = emptyList(),
    val browseSource: Long? = null,
    val browseModes: List<BrowseMode> = emptyList(),
    val browseMode: BrowseMode? = null,
    /** The submitted search; typing alone does not change it. */
    val browseQuery: String = "",
    /** Pages loaded for the current listing; 0 until the first page arrives. */
    val browsePage: Int = 0,
    val browseHasMore: Boolean = false,
    val extensionRows: List<ExtensionRow> = emptyList(),
    val repositories: List<RepositoryView> = emptyList(),
    val pendingRepository: PendingRepository? = null,
    val extensionNotice: String? = null,
    val localExtension: String? = null,
    val appearance: DesktopAppearance = DesktopAppearance(),
    val readerSettings: DesktopReaderSettings = DesktopReaderSettings(),
)

data class BrowseSource(val sourceId: Long, val name: String, val lang: String)

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
    private val extensions: ExtensionManager? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + operationDispatcher)
    private val checkpoints = ReaderCheckpoints(checkpointDao)
    private val closing = AtomicBoolean(false)
    private val storageClosed = AtomicBoolean(false)
    private val closeWrites = Mutex()
    private val operationLock = Any()
    // Installed extensions by package, plus at most one development manifest for this session.
    private val installed = linkedMapOf<String, LocalExtension>()
    private var sessionExtension: LocalExtension? = null
    private var extensionFailures: Map<String, String> = emptyMap()
    private val mutable = MutableStateFlow(ReaderState(library = library.saved(), historyRecording = library.historyRecording(), appearance = library.appearance(), readerSettings = library.readerSettings()))
    // The history worker is drained separately from navigation jobs before closing the store.
    private val historyScope = CoroutineScope(SupervisorJob() + operationDispatcher)
    private val historyWrites = OpenedHistoryWrites(historyScope, DesktopOpenedHistoryDao(library)) { failure ->
        System.getLogger("OpenedHistory").log(System.Logger.Level.WARNING, "Reading history action failed (${failure.javaClass.simpleName})")
    }
    private var lastHistoryOpen: Deferred<Result<Unit>>? = null
    // Reader settings save in order without blocking the reader; close drains them.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val themeModeGeneration = AtomicLong(0)
    private val colorThemeGeneration = AtomicLong(0)
    private val historyRecordingGeneration = AtomicLong(0)
    val state = mutable.asStateFlow()

    init {
        // Installed extensions load from the local store before the first frame; no network access.
        extensions?.let { manager ->
            try {
                val (loaded, failures) = manager.loadInstalled()
                installed.putAll(loaded)
                extensionFailures = failures
                publishSources()
                publishExtensions()
            } catch (failure: Exception) {
                // The library stays usable without extensions; the Extensions screen shows why.
                mutable.update { it.copy(extensionNotice = "Could not load installed extensions: ${failure.message}") }
            }
        }
    }

    private fun extensionFor(sourceId: Long?): LocalExtension? = sourceId?.let { id ->
        installed.values.firstOrNull { it.sourceId == id } ?: sessionExtension?.takeIf { it.sourceId == id }
    }

    private fun browseSources(): List<BrowseSource> = (installed.values + listOfNotNull(sessionExtension))
        .distinctBy { it.sourceId }
        .map { BrowseSource(it.sourceId, it.source.name, it.source.lang.code) }
        .sortedBy { it.name.lowercase() }

    /** Publishes the source list and keeps the Browse selection only while its source is still loaded. */
    private fun publishSources(select: Long? = null) {
        val sources = browseSources()
        mutable.update { current ->
            val keep = select ?: current.browseSource?.takeIf { id -> sources.any { it.sourceId == id } }
            val chosen = keep ?: sources.firstOrNull()?.sourceId
            val modes = extensionFor(chosen)?.source?.let(::browseModes).orEmpty()
            val named = current.copy(sources = sources, browseSource = chosen, sourceName = sources.firstOrNull { it.sourceId == chosen }?.name)
            // A different source starts on its first listing with nothing loaded.
            if (chosen == current.browseSource && current.browseMode in modes) named.copy(browseModes = modes)
            else named.copy(browseModes = modes, browseMode = modes.firstOrNull(), browseQuery = "", browsePage = 0,
                browseHasMore = false, novels = emptyList())
        }
    }

    private fun publishExtensions(notice: String? = null) {
        val manager = extensions ?: return
        val rows = manager.rows(extensionFailures)
        val repositories = manager.repositoryViews()
        mutable.update { it.copy(extensionRows = rows, repositories = repositories, extensionNotice = notice) }
    }

    fun load(manifest: Path) = navigate {
        val loaded = LocalExtension.open(manifest, hostApi)
        if (loaded.packageName in installed || extensions?.installed()?.any { it.pkg == loaded.packageName } == true) {
            loaded.close()
            error("${loaded.source.name} is installed. Remove it before opening a local copy.")
        }
        try { sessionExtension?.close() }
        catch (failure: Exception) { loaded.close(); throw failure }
        sessionExtension = loaded
        val saved = library.saved()
        publishSources(select = loaded.sourceId)
        publishView { it.copy(localExtension = loaded.source.name, view = if (it.view == View.EXTENSIONS) View.EXTENSIONS else View.LIBRARY,
            novels = emptyList(), browsePage = 0, browseHasMore = false, book = null, chapter = null, pages = emptyList(), library = saved,
            projection = null, observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null) }
    }

    fun refreshRepositories() = operate { refreshCatalogs() }

    private fun refreshCatalogs() {
        val manager = extensions ?: return
        check(!state.value.offline) { "Go online to refresh extension repositories" }
        val failures = manager.refresh()
        publishExtensions(failures.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }

    fun installExtension(row: ExtensionRow) = operate {
        val manager = extensions ?: return@operate
        check(!state.value.offline) { "Go online to install extensions" }
        check(sessionExtension?.packageName != row.pkg) { "Close the local copy of ${row.name} first" }
        val loaded = try { manager.install(row.repository, row.pkg) }
        finally { publishExtensions() }
        val replaced = installed.put(row.pkg, loaded)
        extensionFailures = extensionFailures - row.pkg
        runCatching { replaced?.close() }
        publishSources()
        publishExtensions("${loaded.source.name} ${loaded.versionName} is installed")
    }

    fun removeExtension(row: ExtensionRow) = operate {
        val manager = extensions ?: return@operate
        manager.remove(row.pkg)
        extensionFailures = extensionFailures - row.pkg
        installed.remove(row.pkg)?.let { runCatching { it.close() } }
        publishSources()
        publishExtensions("${row.name} is removed. Its library entries and saved chapters are kept.")
    }

    fun checkRepository(indexUrl: String, publicKey: String) = operate {
        val manager = extensions ?: return@operate
        check(!state.value.offline) { "Go online to add a repository" }
        val pending = manager.check(indexUrl, publicKey)
        mutable.update { it.copy(pendingRepository = pending, extensionNotice = null) }
    }

    fun confirmRepository() = operate {
        val manager = extensions ?: return@operate
        val pending = state.value.pendingRepository ?: return@operate
        manager.enroll(pending)
        mutable.update { it.copy(pendingRepository = null) }
        // Packages it owns under this key load now instead of after a restart.
        val (loaded, failures) = manager.loadInstalled { it.repository == pending.trust.repository && it.pkg !in installed }
        installed.putAll(loaded)
        extensionFailures = extensionFailures.filterKeys { it !in loaded } + failures
        publishSources()
        publishExtensions("${pending.trust.repository} is trusted")
    }

    fun cancelRepository() = mutable.update { it.copy(pendingRepository = null) }

    fun setRepositoryEnabled(repository: String, enabled: Boolean) = operate {
        extensions?.setEnabled(repository, enabled) ?: return@operate
        publishExtensions()
    }

    fun removeRepository(repository: String) = operate {
        val manager = extensions ?: return@operate
        manager.removeRepository(repository)
        // Revoked trust stops its extensions now, not only after a restart.
        val revoked = manager.installed().filter { it.repository == repository }.map { it.pkg }
        revoked.forEach { pkg -> installed.remove(pkg)?.let { runCatching { it.close() } } }
        extensionFailures = extensionFailures + revoked.associateWith { "Repository $repository is no longer trusted" }
        publishSources()
        publishExtensions("$repository is removed")
    }

    fun selectSource(sourceId: Long) = navigate {
        check(extensionFor(sourceId) != null) { "This source is no longer installed" }
        publishSources(select = sourceId)
        publishView { it.leaveBook(View.BROWSE) }
        if (state.value.listsWithoutQuery) loadCatalog(reset = true)
    }

    /** Reloads the first page of the current listing. */
    fun browse() = navigate {
        loadCatalog(reset = true)
    }

    fun showBrowse() = navigate {
        showSection(View.BROWSE)
        if (state.value.browsePage == 0 && state.value.listsWithoutQuery) loadCatalog(reset = true)
    }

    fun setBrowseMode(mode: BrowseMode) = navigate {
        check(mode in state.value.browseModes) { "This source does not offer ${mode.label.lowercase()} titles" }
        publishView { it.leaveBook(View.BROWSE).copy(browseMode = mode, browsePage = 0, browseHasMore = false, novels = emptyList()) }
        if (state.value.listsWithoutQuery) loadCatalog(reset = true)
    }

    fun search(query: String) = navigate {
        val text = query.trim()
        require(text.isNotEmpty()) { "Enter a title to search for" }
        check(BrowseMode.SEARCH in state.value.browseModes) { "This source cannot search" }
        publishView { it.leaveBook(View.BROWSE).copy(browseMode = BrowseMode.SEARCH, browseQuery = text, browsePage = 0,
            browseHasMore = false, novels = emptyList()) }
        loadCatalog(reset = true)
    }

    fun loadMore() = navigate {
        check(state.value.view == View.BROWSE && state.value.browseHasMore) { "There are no more titles to load" }
        loadCatalog(reset = false)
    }

    /** Popular and Latest load on arrival; Search waits for a submitted query. */
    private val ReaderState.listsWithoutQuery: Boolean
        get() = !offline && browseSource != null && browseMode != null && browseMode != BrowseMode.SEARCH

    private suspend fun loadCatalog(reset: Boolean) {
        check(!state.value.offline) { "Browsing is unavailable offline" }
        val request = state.value
        val source = extensionFor(request.browseSource)?.source ?: error("Choose a source to browse")
        val mode = request.browseMode ?: error("This source has no catalog to browse")
        val page = if (reset) 1 else request.browsePage + 1
        val result = catalogPage(source, mode, request.browseQuery, page)
        publishView { current ->
            // A result for another source, listing or query is dropped.
            if (current.browseSource != request.browseSource || current.browseMode != mode || current.browseQuery != request.browseQuery) current
            else {
                val listing = mergeCatalog(if (reset) emptyList() else current.novels, result)
                current.leaveBook(View.BROWSE).copy(novels = listing.novels, browsePage = page, browseHasMore = listing.hasMore)
            }
        }
    }

    fun details(novel: Novel) = navigate {
        check(state.value.view == View.BROWSE && novel in state.value.novels) { "This catalog selection is no longer current" }
        check(!state.value.offline) { "Novel refresh is unavailable offline" }
        val loaded = extensionFor(state.value.browseSource) ?: error("This source is no longer installed")
        val source = loaded.source
        check(source is ChapterListSource || source is CursorPaginatedSource) { "This source's chapter list is unsupported" }
        val details = source.getNovelDetails(novel)
        val chapters = sourceChapters(source, details)
        check(chapters.isNotEmpty()) { "The source returned no chapters" }
        val book = library.remember(NovelKey(loaded.sourceId, details.url), details, chapters)
        publishView { it.leaveBook(View.BOOK).copy(book = book, bookOrigin = View.BROWSE) }
    }

    fun showLibrary() {
        synchronized(operationLock) {
            if (!state.value.busy && !closing.get() && state.value.view != View.READER) {
                mutable.update { it.leaveBook(View.LIBRARY) }
            }
        }
        navigate { showSection(View.LIBRARY) }
    }

    fun showHistory() {
        synchronized(operationLock) {
            if (!state.value.busy && !closing.get() && state.value.view != View.READER) {
                mutable.update { it.leaveBook(View.HISTORY) }
            }
        }
        navigate { showSection(View.HISTORY) }
    }

    fun showExtensions() {
        synchronized(operationLock) {
            if (!state.value.busy && !closing.get() && state.value.view != View.READER) {
                mutable.update { it.leaveBook(View.EXTENSIONS) }
            }
        }
        navigate {
            showSection(View.EXTENSIONS)
            publishExtensions()
            // First visit online: fetch catalogs never downloaded. Failures stay on this screen.
            val manager = extensions
            if (manager != null && !state.value.offline && manager.repositoryViews().any { it.enabled && !it.refreshed }) {
                try { refreshCatalogs() } catch (failure: Exception) { publishExtensions(failure.message) }
            }
        }
    }

    fun showSettings() {
        synchronized(operationLock) {
            if (state.value.busy || closing.get()) return
            if (state.value.view != View.READER) {
                mutable.update { it.leaveBook(View.SETTINGS).copy(error = null) }
                return
            }
        }
        navigate { showSection(View.SETTINGS) }
    }

    fun back() = navigate {
        val current = state.value
        when (current.view) {
            View.READER -> publishView { it.leaveBook(View.BOOK).copy(book = it.book) }
            View.BOOK -> showSection(current.bookOrigin)
            else -> Unit
        }
    }

    private suspend fun showSection(section: View) {
        when (section) {
            View.LIBRARY -> {
                val saved = library.saved()
                publishView { it.leaveBook(section).copy(library = saved) }
            }
            View.HISTORY -> {
                lastHistoryOpen?.join()
                val entries = library.openedHistory()
                publishView { it.leaveBook(section).copy(history = entries) }
            }
            else -> publishView { it.leaveBook(section) }
        }
    }

    private fun ReaderState.leaveBook(view: View) = copy(
        view = view, book = null, chapter = null, pages = emptyList(), projection = null,
        observedAnchor = null, storageObservation = null, checkpointLoad = null, positionNotice = null,
    )

    fun setHistoryRecording(enabled: Boolean) {
        if (closing.get()) return
        val gen = historyRecordingGeneration.incrementAndGet()
        mutable.update { it.copy(historyRecording = enabled, error = null) }
        settingsScope.launch {
            try {
                library.setHistoryRecording(enabled)
            } catch (failure: Exception) {
                val stored = runCatching { library.historyRecording() }.getOrElse { !enabled }
                mutable.update { current ->
                    current.copy(
                        historyRecording = if (historyRecordingGeneration.get() == gen) stored else current.historyRecording,
                        error = "Could not save history setting: ${failure.message ?: failure.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun setAppearance(appearance: DesktopAppearance) = updateAppearance(
        updatesThemeMode = true,
        updatesColorTheme = true,
        optimistic = { appearance },
        write = { library.setAppearance(appearance) },
    )

    fun setThemeMode(themeMode: ThemeMode) = updateAppearance(
        updatesThemeMode = true,
        updatesColorTheme = false,
        optimistic = { it.copy(themeMode = themeMode) },
        write = { library.setThemeMode(themeMode) },
    )

    fun setColorTheme(colorTheme: ColorTheme) = updateAppearance(
        updatesThemeMode = false,
        updatesColorTheme = true,
        optimistic = { it.copy(colorTheme = colorTheme) },
        write = { library.setColorTheme(colorTheme) },
    )

    private fun updateAppearance(
        updatesThemeMode: Boolean,
        updatesColorTheme: Boolean,
        optimistic: (DesktopAppearance) -> DesktopAppearance,
        write: () -> Unit,
    ) {
        if (closing.get()) return
        val modeGen = if (updatesThemeMode) themeModeGeneration.incrementAndGet() else null
        val colorGen = if (updatesColorTheme) colorThemeGeneration.incrementAndGet() else null
        mutable.update { it.copy(appearance = optimistic(it.appearance), error = null) }
        settingsScope.launch {
            try {
                write()
            } catch (failure: Exception) {
                val stored = runCatching { library.appearance() }.getOrNull()
                mutable.update { current ->
                    val reconciled = if (stored == null) current.appearance else current.appearance.copy(
                        themeMode = if (modeGen != null && themeModeGeneration.get() == modeGen) stored.themeMode else current.appearance.themeMode,
                        colorTheme = if (colorGen != null && colorThemeGeneration.get() == colorGen) stored.colorTheme else current.appearance.colorTheme,
                    )
                    current.copy(
                        appearance = reconciled,
                        error = "Could not save appearance: ${failure.message ?: failure.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    /** Applies immediately; the save runs in the background and reports a failure as an error. */
    fun setReaderSettings(settings: DesktopReaderSettings) {
        if (closing.get()) return
        val value = settings.normalized()
        mutable.update { it.copy(readerSettings = value) }
        settingsScope.launch {
            try { library.setReaderSettings(value) }
            catch (failure: Exception) {
                mutable.update { it.copy(error = "Could not save reader settings: ${failure.message ?: failure.javaClass.simpleName}") }
            }
        }
    }

    fun readPrevious() = readAdjacent(-1)

    fun readNext() = readAdjacent(1)

    private fun readAdjacent(step: Int) = navigate {
        val current = state.value
        check(current.view == View.READER) { "Open a chapter first" }
        val book = current.book ?: error("Open a novel first")
        val chapter = current.chapter ?: error("Open a chapter first")
        val target = adjacentChapter(book.chapters, chapter, step) ?: error(if (step < 0) "This is the first chapter" else "This is the last chapter")
        readChapter(book, target, current.bookOrigin)
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

    fun openSaved(book: SavedBook) {
        synchronized(operationLock) {
            if (!state.value.busy && !closing.get() && state.value.view != View.READER) {
                mutable.update { it.leaveBook(View.BOOK).copy(book = book, bookOrigin = View.LIBRARY) }
            }
        }
        navigate {
            val stored = library.book(book.key)
            publishView { it.leaveBook(View.BOOK).copy(book = stored, bookOrigin = View.LIBRARY) }
        }
    }

    fun favorite() = operate {
        val current = state.value.book ?: return@operate
        val book = setMembership(current.key, !current.favorite)
        mutable.update { it.copy(book = book, library = library.saved()) }
    }

    fun read(chapter: Chapter) = navigate {
        val book = state.value.book ?: error("Open a novel first")
        require(!chapter.locked && book.chapters.any { it == chapter }) { "This chapter is locked or unavailable" }
        readChapter(book, chapter, state.value.bookOrigin)
    }

    fun readHistory(entry: DesktopHistoryEntry) = navigate {
        require(state.value.view == View.HISTORY && entry in state.value.history) { "This history entry is no longer current" }
        val key = entry.chapter.key
        val book = library.book(key.novel) ?: error("This novel is no longer available")
        val chapter = book.chapters.find { it.url == key.url && !it.locked }
            ?: error("This chapter is locked or unavailable")
        readChapter(book, chapter, View.HISTORY)
    }

    private suspend fun readChapter(book: SavedBook, chapter: Chapter, origin: View) {
        val key = ChapterKey(book.key, chapter.url)
        historyWrites.open(incognito = !state.value.historyRecording, openedAtMillis = System.currentTimeMillis()) {
            DesktopOpenedChapter(key, book.novel.title, chapter.name)
        }?.let { lastHistoryOpen = it }
        // Retire the old view before storage/source suspension can admit stale callbacks.
        publishView { it.leaveBook(View.BOOK).copy(book = book, bookOrigin = origin) }
        val loadedChapter = try {
            chapterLoader.load(key) {
                when {
                    state.value.offline -> ChapterPageAccess.Unavailable(ChapterLoadFailure.OFFLINE)
                    else -> {
                        val loaded = extensionFor(book.key.sourceId)
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
                ChapterLoadFailure.SOURCE_UNAVAILABLE -> "Install this novel's extension to read chapters that have not been downloaded"
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
            settingsScope.coroutineContext.job.children.toList().joinAll()
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
        settingsScope.coroutineContext.job.cancelAndJoin()
        withContext(Dispatchers.IO) {
            (installed.values + listOfNotNull(sessionExtension)).forEach { runCatching { it.close() } }
            installed.clear()
            sessionExtension = null
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
                catch (fatal: VirtualMachineError) { resumeClaimedReader(previous); throw fatal }
                // Extension code can fail with an Error (initializer, linkage); report it like any failure.
                catch (failure: Throwable) {
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
