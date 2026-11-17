package io.grimoire.app.ui.screen.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.source.web.PageListSource
import io.grimoire.app.R
import io.grimoire.app.data.reading.CompletionSaveNotice
import io.grimoire.app.util.imageUrl
import io.grimoire.app.util.isSeparator
import io.grimoire.app.util.text
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.book.LOCAL_PKG
import io.grimoire.api.source.sourceIdFor
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.ReadingHistoryDao
import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.ReaderTextAlign
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import io.grimoire.app.util.ContentLanguages
import io.grimoire.reading.anchor.ReaderCheckpoints
import io.grimoire.reading.anchor.ReaderCheckpointLoad
import io.grimoire.reading.anchor.CheckpointNotice
import io.grimoire.reading.anchor.LegacyReaderViewport
import io.grimoire.reading.storage.ReadingCheckpointDao
import io.grimoire.reading.storage.ReadingStateDao
import io.grimoire.reading.storage.ReadingStateRejection
import io.grimoire.reading.storage.ReadingStateSession
import io.grimoire.reading.state.*
import io.grimoire.reading.anchor.ReadingProjection
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.ChapterLoader
import io.grimoire.reading.ChapterOrigin
import io.grimoire.reading.ChapterPageAccess
import io.grimoire.reading.ChapterLoadFailure
import io.grimoire.reading.ChapterLoadException
import io.grimoire.app.data.local.entity.effectiveTotal
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.data.preferences.ReaderPreferences
import io.grimoire.app.data.preferences.TtsPreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.data.tts.TtsController
import io.grimoire.app.data.tts.TtsChapterContent
import io.grimoire.app.data.tts.TtsChapterInput
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.data.tts.TtsPlaybackState
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val chapterDao: ChapterDao,
    private val chapterLoader: ChapterLoader,
    checkpointDao: ReadingCheckpointDao,
    completionFacade: ReadingStateFacade,
    readingStateDao: ReadingStateDao,
    private val novelDao: NovelDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val incognitoManager: io.grimoire.app.data.preferences.IncognitoManager,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val ttsController: TtsController,
    private val ttsPreferences: TtsPreferences,
    private val chapterImageStore: ChapterImageStore,
    private val analytics: io.grimoire.app.data.analytics.Analytics,
) : ViewModel() {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    private val localizedContext = AppLocale.wrap(context)

    val pkg: String = checkNotNull(savedStateHandle["pkg"])
    private val novelUrl: String = checkNotNull(savedStateHandle["novelUrl"])
    private val navNovelId: Long? = savedStateHandle.get<Long>("novelId")?.takeIf { it > 0L }

    // Written back on every chapter change (navigatePrev/Next, TTS auto-advance), so
    // a process-death restore reopens the chapter the user is actually on rather than
    // the one they entered the reader with.
    private val initialChapterUrl: String = checkNotNull(savedStateHandle["chapterUrl"])

    private val source get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }?.source

    val chapterWebUrl: String get() {
        val url = _chapters.value.getOrNull(_currentIndex.value)?.url ?: return ""
        if (url.startsWith("http")) return url
        val baseUrl = extensionManager.extensions.value
            .firstOrNull { it.info.packageName == pkg }
            ?.source?.javaClass?.getAnnotation(SourceInfo::class.java)?.baseUrl ?: return url
        return "$baseUrl$url"
    }

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    private val _currentIndex = MutableStateFlow(-1)

    private val checkpoints = ReaderCheckpoints(checkpointDao)
    private val completionCommands = ReadingCompletionCommands(completionFacade)
    private val visualWriter = ReaderVisualWriter(readingStateDao, completionFacade)
    private var completionNovelKey: NovelKey? = null
    private val requestedCompletion = mutableMapOf<ChapterKey, Pair<AcceptedReadingCommand, Boolean>>()
    private val _completionNotice = MutableStateFlow<CompletionSaveNotice?>(null)
    internal val completionNotice: StateFlow<CompletionSaveNotice?> = _completionNotice.asStateFlow()
    private var leaving = false
    private var cleared = false
    private val _saveError = MutableStateFlow<String?>(null)
    internal val saveError: StateFlow<String?> = _saveError.asStateFlow()
    private val _positionNotice = MutableStateFlow<CheckpointNotice?>(null)
    internal val positionNotice: StateFlow<CheckpointNotice?> = _positionNotice.asStateFlow()
    private val livePosition = MutableStateFlow<ReaderPosition?>(null)
    private val persistenceJob = SupervisorJob()
    private val persistenceScope = CoroutineScope(persistenceJob + Dispatchers.Main.immediate)
    private val progressWriter = ReaderProgressWriter(persistenceScope, onFailure = ::positionSaveFailed) { position ->
        checkpoints.flush()
        val checkpoint = position.checkpoint
        if (checkpoint != null && checkpoints.isRevoked(checkpoint)) {
            if (_content.value?.checkpoint === checkpoint) _positionNotice.value = CheckpointNotice.RELOAD
        } else {
            chapterDao.setReadAnchor(position.chapterId, position.fraction, position.anchorIndex, position.anchorOffset)
        }
        _saveError.value = null
    }

    val currentChapter: StateFlow<ChapterEntity?> = combine(_chapters, _currentIndex, livePosition) { chapters, idx, position ->
        chapters.getOrNull(idx)?.withReaderPosition(position)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPrev: StateFlow<Boolean> = combine(_chapters, _currentIndex) { _, idx ->
        idx > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasNext: StateFlow<Boolean> = combine(_chapters, _currentIndex) { chapters, idx ->
        idx >= 0 && idx < chapters.size - 1
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _content = MutableStateFlow<ReaderContent?>(null)
    internal val content: StateFlow<ReaderContent?> = _content.asStateFlow()
    // Compatibility projections. The screen observes content atomically instead.
    val pages: StateFlow<List<NovelPage>> = _content.map { it?.pages.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pagesChapterId: StateFlow<Long?> = _content.map { it?.chapterId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val loadedPages get() = _content.value?.pages.orEmpty()
    private var loadPagesJob: Job? = null

    // Per-novel text alignment + the novel's language (for AUTO direction).
    // Loaded with the chapter list; the setter persists on the novel row.
    private var novelIdForAlign = -1L
    private val _textAlign = MutableStateFlow(ReaderTextAlign.AUTO)
    val textAlign: StateFlow<ReaderTextAlign> = _textAlign.asStateFlow()
    private val _novelLanguage = MutableStateFlow<Language?>(null)
    val novelLanguage: StateFlow<Language?> = _novelLanguage.asStateFlow()

    fun setTextAlign(value: ReaderTextAlign) {
        _textAlign.value = value
        val id = novelIdForAlign
        if (id > 0L) viewModelScope.launch { novelDao.updateReaderTextAlign(id, value.ordinal) }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val markAsReadStrategy: StateFlow<MarkAsReadStrategy> =
        readerPreferences.markAsReadStrategy.stateIn(viewModelScope)
    val markAsReadThreshold: StateFlow<Int> = readerPreferences.markAsReadThreshold.stateIn(viewModelScope)
    val markAsReadParagraphsFromEnd: StateFlow<Int> =
        readerPreferences.markAsReadParagraphsFromEnd.stateIn(viewModelScope)
    val fontSize: StateFlow<Int> = readerPreferences.fontSize.stateIn(viewModelScope)
    val lineHeightTimes10: StateFlow<Int> = readerPreferences.lineHeightTimes10.stateIn(viewModelScope)
    val paragraphSpacing: StateFlow<Int> = readerPreferences.paragraphSpacing.stateIn(viewModelScope)
    val readerFont: StateFlow<ReaderFont> = readerPreferences.readerFont.stateIn(viewModelScope)
    val colorTheme: StateFlow<ReaderColorTheme?> = readerPreferences.colorTheme.stateIn(viewModelScope)
    val orientation: StateFlow<ReaderOrientation> = readerPreferences.orientation.stateIn(viewModelScope)
    val hideNotificationBar: StateFlow<Boolean> = readerPreferences.hideNotificationBar.stateIn(viewModelScope)
    val hideInlineImages: StateFlow<Boolean> = readerPreferences.hideInlineImages.stateIn(viewModelScope)
    val showChapterProgressPercent: StateFlow<Boolean> =
        readerPreferences.showChapterProgressPercent.stateIn(viewModelScope)
    val showNovelProgressPercent: StateFlow<Boolean> =
        readerPreferences.showNovelProgressPercent.stateIn(viewModelScope)
    val grimoireEasterEggEnabled: StateFlow<Boolean> =
        readerPreferences.grimoireEasterEggEnabled.stateIn(viewModelScope)

    /**
     * Fraction (0..1) of chapters in the current chapter's novel that are marked read. Computed
     * by chapter count from the existing [ChapterDao.getStatsForNovel] query. Honors the library's
     * "Include locked chapters in totals" preference so the in-reader Book % matches what the
     * library row shows for the same novel.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val novelProgress: StateFlow<Float> = currentChapter
        .filterNotNull()
        .distinctUntilChangedBy { it.novelId }
        .flatMapLatest { chapter ->
            combine(chapterDao.getStatsForNovel(chapter.novelId), libraryPreferences.includeLockedInTotals.changes()) { stats, includeLocked ->
                if (stats == null) return@combine 0f
                val denom = stats.effectiveTotal(includeLocked)
                if (denom <= 0) 0f else stats.readCount.toFloat() / denom
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _revealedImageUrls = MutableStateFlow<Set<String>>(emptySet())
    val revealedImageUrls: StateFlow<Set<String>> = _revealedImageUrls.asStateFlow()

    fun toggleImageReveal(url: String) {
        _revealedImageUrls.update { if (url in it) it - url else it + url }
    }

    fun revealAllImagesInCurrentChapter() {
        val urls = loadedPages.mapNotNull { it.imageUrl }
        if (urls.isEmpty()) return
        _revealedImageUrls.update { it + urls }
    }

    fun hideAllImagesInCurrentChapter() {
        val urls = loadedPages.mapNotNull { it.imageUrl }.toSet()
        if (urls.isEmpty()) return
        _revealedImageUrls.update { it - urls }
    }

    fun setHideInlineImages(value: Boolean) = viewModelScope.launch {
        readerPreferences.hideInlineImages.set(value)
    }

    fun setShowChapterProgressPercent(value: Boolean) = viewModelScope.launch {
        readerPreferences.showChapterProgressPercent.set(value)
    }

    fun setShowNovelProgressPercent(value: Boolean) = viewModelScope.launch {
        readerPreferences.showNovelProgressPercent.set(value)
    }

    fun setGrimoireEasterEggEnabled(value: Boolean) = viewModelScope.launch {
        readerPreferences.grimoireEasterEggEnabled.set(value)
    }

    fun setMarkAsReadStrategy(value: MarkAsReadStrategy) = viewModelScope.launch {
        readerPreferences.markAsReadStrategy.set(value)
    }

    fun setMarkAsReadThreshold(percent: Int) = viewModelScope.launch {
        readerPreferences.markAsReadThreshold.set(percent.coerceIn(50, 100))
    }

    fun setMarkAsReadParagraphsFromEnd(n: Int) = viewModelScope.launch {
        readerPreferences.markAsReadParagraphsFromEnd.set(n.coerceIn(0, 20))
    }

    /** Raw read-aloud playback state, shared across the whole app. */
    val ttsState: StateFlow<TtsPlaybackState> = ttsController.state

    /** URL of the chapter TTS is currently reading, or null when nothing is playing. */
    val ttsCurrentUrl: StateFlow<String?> = ttsController.nowPlaying
        .map { it?.chapterUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** [NovelPage.index] of the paragraph TTS is speaking in this chapter, else null. */
    val ttsSpokenPageIndex: StateFlow<Int?> = combine(
        ttsController.state, ttsController.nowPlaying, ttsController.progress, currentChapter,
    ) { state, nowPlaying, progress, chapter ->
        val active = state == TtsPlaybackState.PLAYING || state == TtsPlaybackState.PAUSED
        if (active && nowPlaying != null && chapter != null &&
            nowPlaying.chapterUrl == chapter.url && progress.currentPageIndex >= 0
        ) {
            progress.currentPageIndex
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ttsError: StateFlow<String?> = ttsController.errorMessage
    internal val ttsCompletionNotice = ttsController.completionNotice

    val ttsEnabled: StateFlow<Boolean> = ttsPreferences.enabled.stateIn(viewModelScope)
    val ttsEngine: StateFlow<TtsEngineType> = ttsPreferences.engine.stateIn(viewModelScope)
    val ttsSpeechRate: StateFlow<Int> = ttsPreferences.speechRate.stateIn(viewModelScope)
    val ttsPitch: StateFlow<Int> = ttsPreferences.pitch.stateIn(viewModelScope)
    val ttsAutoAdvance: StateFlow<Boolean> = ttsPreferences.autoAdvance.stateIn(viewModelScope)

    init {
        viewModelScope.launch {
            val chapter = if (navNovelId != null) {
                chapterDao.getByUrl(navNovelId, initialChapterUrl)
            } else {
                val sourceId = if (pkg == LOCAL_PKG) LOCAL_SOURCE_ID else sourceIdFor(pkg)
                chapterDao.getByNovelUrl(sourceId, novelUrl, initialChapterUrl)
            } ?: run {
                _error.value = localizedContext.getString(R.string.error_chapter_not_found)
                _isLoading.value = false
                return@launch
            }
            novelDao.updateLastReadAt(chapter.novelId, System.currentTimeMillis())
            novelDao.getById(chapter.novelId)?.let { novel ->
                completionNovelKey = NovelKey(novel.sourceId, novel.url)
                novelIdForAlign = novel.id
                _textAlign.value =
                    ReaderTextAlign.entries.getOrElse(novel.readerTextAlign) { ReaderTextAlign.AUTO }
                _novelLanguage.value = novel.language?.let { ContentLanguages.parse(it) }
            }
            recordHistory(chapter)
            // Metadata only — the per-chapter content is read on demand in loadPages
            // (via getContent). Pulling every chapter's downloaded text here made
            // opening a heavily-downloaded novel slow.
            val allChapters = chapterDao.getChapterMetadataOnce(chapter.novelId)
            _chapters.value = allChapters
            _currentIndex.value = allChapters.indexOfFirst { it.url == initialChapterUrl }.coerceAtLeast(0)
            loadPages()
            trackReaderOpened()
        }
        // Keep the reader in sync when TTS auto-advances or skips chapters.
        viewModelScope.launch {
            ttsController.chapterContent.drop(1).collect { content ->
                content ?: return@collect
                val url = content.nowPlaying.chapterUrl
                if (ttsController.state.value == TtsPlaybackState.IDLE) return@collect
                val index = content.readerChapterIndex(pkg, _chapters.value)
                if (index >= 0 && index != _currentIndex.value) {
                    flushProgress()
                    _currentIndex.value = index
                    onChapterChanged(url)
                    loadPages(preloaded = content)
                }
            }
        }
    }

    /**
     * Fires once per reader open, tagged with the source it was opened on. Awaits the
     * extension scan so the label resolves (the list can be empty this early). Anonymous
     * by design — source identity only, never the novel title. Dropped unless opted in.
     */
    private suspend fun trackReaderOpened() {
        extensionManager.awaitReady()
        val loaded = extensionManager.extensions.value.firstOrNull { it.info.packageName == pkg }
        analytics.track(
            io.grimoire.app.data.analytics.AnalyticsEvent.READER_OPENED,
            mapOf(
                "source_id" to (loaded?.id ?: sourceIdFor(pkg)).toString(),
                "source" to (loaded?.info?.label ?: if (pkg == LOCAL_PKG) "Local" else "Unknown"),
                "source_type" to if (pkg == LOCAL_PKG) "local" else "extension",
            ),
        )
    }

    /** Records a chapter switch for history and process-death restore. */
    private fun onChapterChanged(url: String) {
        savedStateHandle["chapterUrl"] = url
        _chapters.value.firstOrNull { it.url == url }?.let { recordHistory(it) }
    }

    /**
     * Logs an opened chapter to the reading history (newest-first), unless incognito is on.
     * The novel row supplies the denormalized title/cover/url snapshot so the entry renders
     * even after the chapter is pruned. Best-effort: never blocks or fails chapter loading.
     */
    private fun recordHistory(chapter: ChapterEntity) {
        if (incognitoManager.enabled.value) return
        viewModelScope.launch {
            val novel = novelDao.getById(chapter.novelId) ?: return@launch
            readingHistoryDao.upsert(
                ReadingHistoryEntity(
                    sourcePackage = pkg,
                    novelId = novel.id,
                    novelUrl = novel.url,
                    novelTitle = novel.title,
                    novelThumbnailUrl = novel.thumbnailUrl,
                    chapterUrl = chapter.url,
                    chapterName = chapter.name,
                    chapterNumber = chapter.chapterNumber,
                    openedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun loadPages() = loadPages(preloaded = null)

    private fun loadPages(preloaded: TtsChapterContent?) {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        checkpoints.retire()
        flushProgress()
        loadPagesJob?.cancel()
        _content.value = null
        visualWriter.setTarget(null)
        persistenceScope.launch { synchronizeVisualWriter() }
        _isLoading.value = true
        _error.value = null
        loadPagesJob = viewModelScope.launch {
            try {
                progressWriter.flush()?.join()
                check(!progressWriter.hasPending()) { localizedContext.getString(R.string.reader_position_save_error) }

                // A download may have completed since the chapter list was loaded.
                val fresh = chapterDao.getByUrl(chapter.novelId, chapter.url) ?: chapter
                val key = novelDao.getById(fresh.novelId)?.let { novel ->
                    ChapterKey(NovelKey(novel.sourceId, novel.url), fresh.url)
                }
                val result = chapterLoader.load(key, preloaded?.pages) {
                    extensionManager.awaitReady()
                    currentCoroutineContext().ensureActive()
                    val src = source
                    val pageSource = src as? PageListSource
                    when {
                        src == null -> ChapterPageAccess.Unavailable(ChapterLoadFailure.SOURCE_UNAVAILABLE)
                        pageSource == null -> ChapterPageAccess.Unavailable(ChapterLoadFailure.PAGES_UNSUPPORTED)
                        else -> ChapterPageAccess.Available { pageSource.getPageList(fresh.toChapter()) }
                    }
                }.let { loaded ->
                    if (loaded.origin == ChapterOrigin.PRELOADED) {
                        // A follower reuses the playback load's observation, never the newer read
                        // performed while checking for a saved copy.
                        loaded.copy(storageObservation = preloaded?.observation?.takeIf { it.key == key })
                    } else loaded
                }
                val loaded = withContext(Dispatchers.IO) {
                    androidReaderContent(chapter.id, key, result) { page ->
                        chapterImageStore.localImageUri(fresh.novelId, fresh.url, page.index, result.cacheRevision)
                    }
                }
                val prepared = prepareReaderCheckpoint(loaded, checkpoints,
                    LegacyReaderViewport(fresh.readAnchorItemIndex, fresh.readAnchorItemOffset).takeIf {
                        fresh.readAnchorItemIndex != 0 || fresh.readAnchorItemOffset != 0 || fresh.readProgress != 0f
                    })
                // Some extensions return normally even after cancellation.
                currentCoroutineContext().ensureActive()
                if (_chapters.value.getOrNull(_currentIndex.value)?.id != chapter.id) return@launch
                _content.value = prepared
                _positionNotice.value = prepared.checkpoint?.restore?.notice
                persistenceScope.launch { synchronizeVisualWriter() }
                recordWordCount(fresh, loaded.pages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ChapterLoadException) {
                currentCoroutineContext().ensureActive()
                _error.value = when (failure.reason) {
                    ChapterLoadFailure.SOURCE_UNAVAILABLE -> localizedContext.getString(R.string.error_source_not_available)
                    ChapterLoadFailure.PAGES_UNSUPPORTED -> "IllegalStateException: ${localizedContext.getString(R.string.error_no_readable_pages)}"
                    ChapterLoadFailure.OFFLINE -> error("Android reader does not restrict source access to offline mode")
                }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _error.value = "${error::class.simpleName}: ${error.message ?: "(no message)"}"
            } finally {
                if (currentCoroutineContext().isActive) _isLoading.value = false
            }
        }
    }

    private fun recordWordCount(chapter: ChapterEntity, pages: List<NovelPage>) {
        val words = pages.sumOf { it.text.countWords() }
        if (words <= 0 || words == chapter.wordCount) return
        viewModelScope.launch { chapterDao.setWordCount(chapter.id, words) }
        _chapters.update { list ->
            list.map { if (it.id == chapter.id) it.copy(wordCount = words) else it }
        }
    }

    fun navigatePrev() {
        if (leaving) return
        if (_currentIndex.value > 0) {
            flushProgress()
            _currentIndex.value--
            _chapters.value.getOrNull(_currentIndex.value)?.url?.let { onChapterChanged(it) }
            loadPages()
        }
    }

    fun navigateNext() {
        if (leaving) return
        if (_currentIndex.value < _chapters.value.size - 1) {
            flushProgress()
            val chapter = _chapters.value.getOrNull(_currentIndex.value)
            if (chapter != null && chapter.id == _content.value?.chapterId &&
                !(completionKey(chapter)?.let { requestedCompletion[it]?.second } ?: chapter.read)) {
                completionKey(chapter)?.let { key ->
                    submitCompletion(ReadingCompletionInput.Next(key, _content.value?.projection?.document?.key, true, completionTime()), true)
                }
            }
            _currentIndex.value++
            _chapters.value.getOrNull(_currentIndex.value)?.url?.let { onChapterChanged(it) }
            loadPages()
        }
    }

    fun updateProgress(
        chapterId: Long,
        fraction: Float,
        anchorIndex: Int,
        anchorOffset: Int,
        projection: ReadingProjection,
    ) {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        if (leaving) return
        val checkpoint = _content.value?.checkpoint
        if (checkpoint != null && !checkpoints.canRecordPosition(checkpoint)) return
        val position = captureReaderPosition(chapter.id, _content.value, projection,
            chapterId, fraction, anchorIndex, anchorOffset) ?: return
        if (checkpoint != null && checkpoints.capture(checkpoint, anchorIndex) != null) _positionNotice.value = null
        livePosition.value = position
        progressWriter.submit(position)
    }

    internal fun visualAuthority(projection: ReadingProjection): ReadingStateSession? =
        if (leaving) null else visualWriter.authority(projection)

    internal fun completeFromVisualInput(
        session: ReadingStateSession,
        projection: ReadingProjection,
        viewport: AndroidViewportV1,
    ) {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        val content = _content.value ?: return
        if (leaving || chapter.read || chapter.id != content.chapterId ||
            content.checkpoint?.let(checkpoints::canRecordPosition) != true ||
            viewport.total != projection.itemCount) return
        val policy = readerAutomaticPolicy(
            markAsReadStrategy.value, markAsReadThreshold.value, markAsReadParagraphsFromEnd.value,
        )
        val command = visualWriter.capture(session, projection, viewport, policy, completionTime()) ?: return
        completionCommands.acceptAutomatic(command)
        persistenceScope.launch { flushCompletions() }
    }

    fun flushProgress() {
        livePosition.value?.let { position ->
            _chapters.update { list -> list.map { it.withReaderPosition(position) } }
            livePosition.value = null
        }
        progressWriter.flush()
    }

    override fun onCleared() {
        cleared = true
        leaving = true
        visualWriter.setTarget(null)
        checkpoints.retire()
        flushProgress()
        persistenceScope.launch { flushCompletions() }
        persistenceScope.launch { try { checkpoints.flush() } catch (failure: Exception) { positionSaveFailed(failure) } }
        // Finish queued writes after viewModelScope is cancelled, then release this scope.
        persistenceJob.complete()
        super.onCleared()
    }

    fun userPosition(projection: ReadingProjection) {
        if (!leaving && _content.value?.projection === projection) _content.value?.checkpoint?.let(checkpoints::userPosition)
    }

    fun acceptCheckpointPosition(projection: ReadingProjection, index: Int) {
        if (leaving || _content.value?.projection !== projection) return
        val load = _content.value?.checkpoint ?: return
        checkpoints.userPosition(load)
        if (checkpoints.capture(load, index) != null) { _positionNotice.value = null; retryPositionSave() }
    }

    private fun positionSaveFailed(failure: Exception) {
        if (failure is CancellationException) throw failure
        _saveError.value = localizedContext.getString(R.string.reader_position_save_error)
    }

    fun dismissPositionSaveError() { _saveError.value = null }

    fun retryPositionSave() = persistenceScope.launch {
        try {
            progressWriter.flush()?.join()
            checkpoints.flush()
            if (!progressWriter.hasPending()) _saveError.value = null
            _content.value?.checkpoint?.takeIf(checkpoints::isRevoked)?.let { _positionNotice.value = CheckpointNotice.RELOAD }
        } catch (failure: Exception) { positionSaveFailed(failure) }
    }

    /** Explicit back navigation waits for confirmation; failed writes keep this reader and its pending captures. */
    suspend fun flushForExit(): Boolean {
        if (leaving) return false
        leaving = true
        val load = _content.value?.checkpoint
        checkpoints.retire()
        return try {
            flushCompletions()
            flushProgress()
            progressWriter.flush()?.join()
            checkpoints.flush()
            if (!progressWriter.hasPending() && !completionCommands.hasPending() && _completionNotice.value == null) {
                visualWriter.setTarget(null)
                synchronizeVisualWriter()
            }
            if (progressWriter.hasPending() || completionCommands.hasPending() || _completionNotice.value != null) {
                resumeAfterFailedExit(load)
                false
            } else true
        } catch (failure: Exception) {
            resumeAfterFailedExit(load)
            positionSaveFailed(failure)
            false
        }
    }

    private fun resumeAfterFailedExit(load: ReaderCheckpointLoad?) {
        if (cleared) return
        leaving = false
        load?.let(checkpoints::resume)
        persistenceScope.launch { synchronizeVisualWriter() }
    }

    fun toggleRead() {
        if (leaving) return
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        val key = completionKey(chapter) ?: return
        val next = !(requestedCompletion[key]?.second ?: chapter.read)
        submitCompletion(if (next) ReadingCompletionInput.MarkRead(key, completionTime()) else ReadingCompletionInput.MarkUnread(key), next)
    }

    private fun completionKey(chapter: ChapterEntity): ChapterKey? = completionNovelKey
        ?.takeIf { chapter.novelId == novelIdForAlign }?.let { ChapterKey(it, chapter.url) }

    // Preserve the legacy SQL clock precision, captured when input is accepted, not on retry.
    private fun completionTime(): Long = (System.currentTimeMillis() / 1000) * 1000

    private fun submitCompletion(input: ReadingCompletionInput, desired: Boolean) {
        if (leaving) return
        val accepted = completionCommands.accept(input)
        requestedCompletion[input.key] = accepted to desired
        if (_content.value?.projection?.document?.key == input.key) visualWriter.setTarget(null)
        persistenceScope.launch { flushCompletions() }
    }

    private suspend fun flushCompletions() {
        var notice: CompletionSaveNotice? = null
        for (attempt in completionCommands.flush()) {
            when (val outcome = attempt.outcome) {
                is ReadingCommandOutcome.Failed -> notice = CompletionSaveNotice.SAVE_FAILED
                ReadingCommandOutcome.Missing -> if (notice == null) notice = CompletionSaveNotice.CHANGED
                is ReadingCommandOutcome.Saved -> {
                    val confirmation = outcome.save.confirmation
                    val visual = attempt.accepted.input as? ReadingCompletionInput.Visual
                    val rejection = (confirmation as? ReadingStateConfirmation.NotApplied)?.reason
                    if (visual != null && rejection == ReadingStateRejection.STALE)
                        visualWriter.invalidate(visual.evidence.loadId)
                    if (outcome.save.current == null && notice == null) notice = CompletionSaveNotice.CHANGED
                    if (outcome.save.wordCount is ReadingWordCountOutcome.Failed && notice != CompletionSaveNotice.SAVE_FAILED)
                        notice = CompletionSaveNotice.WORD_COUNT_FAILED
                    val expiredVisual = visual != null &&
                        (rejection == ReadingStateRejection.STALE || rejection == ReadingStateRejection.INELIGIBLE)
                    if (notice == null && rejection != null && rejection != ReadingStateRejection.NO_CHANGE && !expiredVisual)
                        notice = CompletionSaveNotice.CHANGED
                    if (completionCommands.isLatest(attempt.accepted)) outcome.save.current?.let { current ->
                        _chapters.update { list -> list.map {
                            if (completionKey(it) == current.key) it.copy(read = current.status == ReadStatus.READ, firstReadAt = current.firstReadAt,
                                readProgress = if (attempt.accepted.input is ReadingCompletionInput.Next && current.status == ReadStatus.READ) 1f else it.readProgress)
                            else it
                        } }
                    }
                }
            }
            if (requestedCompletion[attempt.accepted.input.key]?.first === attempt.accepted &&
                attempt.outcome !is ReadingCommandOutcome.Failed)
                requestedCompletion.remove(attempt.accepted.input.key)
        }
        // An earlier uncertain command may have blocked a later input in this flush.
        _completionNotice.value = notice ?: when {
            completionCommands.hasPending() -> CompletionSaveNotice.SAVE_FAILED
            _completionNotice.value == CompletionSaveNotice.CHANGED -> CompletionSaveNotice.CHANGED
            else -> null
        }
        synchronizeVisualWriter()
    }

    private suspend fun synchronizeVisualWriter() {
        if (!leaving) {
            val content = _content.value
            val key = content?.projection?.document?.key
            visualWriter.setTarget(content?.takeIf { key != null && key !in requestedCompletion })
        }
        try {
            visualWriter.synchronize(register = !leaving)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _completionNotice.value = CompletionSaveNotice.SAVE_FAILED
        }
    }

    fun dismissCompletionError() { _completionNotice.value = null }

    fun retryCompletionSave() = persistenceScope.launch { flushCompletions() }

    fun setFontSize(sp: Int) = viewModelScope.launch {
        readerPreferences.fontSize.set(sp.coerceIn(12, 32))
    }

    fun setLineHeight(times10: Int) = viewModelScope.launch {
        readerPreferences.lineHeightTimes10.set(times10.coerceIn(10, 30))
    }

    fun setParagraphSpacing(dp: Int) = viewModelScope.launch {
        readerPreferences.paragraphSpacing.set(dp.coerceIn(0, 32))
    }

    fun setReaderFont(font: ReaderFont) = viewModelScope.launch {
        readerPreferences.readerFont.set(font)
    }

    fun setColorTheme(theme: ReaderColorTheme) = viewModelScope.launch {
        readerPreferences.colorTheme.set(theme)
    }

    fun setOrientation(value: ReaderOrientation) = viewModelScope.launch {
        readerPreferences.orientation.set(value)
    }

    /** Starts reading the current chapter aloud, or toggles play/pause if already active. */
    fun toggleTts() {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        val activeForThisChapter = ttsController.nowPlaying.value?.chapterUrl == chapter.url &&
            ttsController.state.value != TtsPlaybackState.IDLE &&
            ttsController.state.value != TtsPlaybackState.ERROR
        if (activeForThisChapter) {
            ttsController.togglePlayPause()
        } else {
            ttsController.play(
                pkg = pkg,
                novelId = chapter.novelId,
                chapterUrl = chapter.url,
                chapters = _chapters.value,
                startIndex = _currentIndex.value,
                input = _content.value?.takeIf { it.chapterId == chapter.id }?.let {
                    TtsChapterInput(it.pages, it.projection.document, it.storageObservation)
                },
            )
        }
    }

    fun stopTts() = ttsController.stop()

    internal fun dismissTtsCompletionError() = ttsController.dismissCompletionError()
    internal fun retryTtsCompletionSave() = ttsController.retryCompletionSave()

    fun clearTtsError() {
        ttsController.consumeError()
    }

    fun setTtsEnabled(value: Boolean) = viewModelScope.launch {
        ttsPreferences.enabled.set(value)
        if (!value) ttsController.stop()
    }

    fun setTtsEngine(value: TtsEngineType) = viewModelScope.launch {
        ttsPreferences.engine.set(value)
    }

    fun setTtsSpeechRate(percent: Int) = viewModelScope.launch {
        ttsPreferences.speechRate.set(percent.coerceIn(25, 300))
    }

    fun setTtsPitch(percent: Int) = viewModelScope.launch {
        ttsPreferences.pitch.set(percent.coerceIn(50, 200))
    }

    fun setTtsAutoAdvance(value: Boolean) = viewModelScope.launch {
        ttsPreferences.autoAdvance.set(value)
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)

private fun String.countWords(): Int {
    var count = 0
    var inWord = false
    for (ch in this) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}
