package io.grimoire.app.ui.screen.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.source.web.PageListSource
import io.grimoire.app.R
import io.grimoire.app.util.imageUrl
import io.grimoire.app.util.isSeparator
import io.grimoire.app.util.text
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.epub.LOCAL_PKG
import io.grimoire.api.source.sourceIdFor
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.ReadingHistoryDao
import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.ReaderTextAlign
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import io.grimoire.app.util.ContentLanguages
import io.grimoire.app.data.local.entity.decodeChapterContent
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
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val chapterDao: ChapterDao,
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

    private val livePosition = MutableStateFlow<ReaderPosition?>(null)
    private val persistenceJob = SupervisorJob()
    private val persistenceScope = CoroutineScope(persistenceJob + Dispatchers.Main.immediate)
    private val progressWriter = ReaderProgressWriter(persistenceScope) { position ->
        chapterDao.setReadAnchor(position.chapterId, position.fraction, position.anchorIndex, position.anchorOffset)
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

    private val _pages = MutableStateFlow<List<NovelPage>>(emptyList())
    val pages: StateFlow<List<NovelPage>> = _pages.asStateFlow()
    private val _pagesChapterId = MutableStateFlow<Long?>(null)
    val pagesChapterId: StateFlow<Long?> = _pagesChapterId.asStateFlow()
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
        val urls = _pages.value.mapNotNull { it.imageUrl }
        if (urls.isEmpty()) return
        _revealedImageUrls.update { it + urls }
    }

    fun hideAllImagesInCurrentChapter() {
        val urls = _pages.value.mapNotNull { it.imageUrl }.toSet()
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

    val ttsEnabled: StateFlow<Boolean> = ttsPreferences.enabled.stateIn(viewModelScope)
    val ttsEngine: StateFlow<TtsEngineType> = ttsPreferences.engine.stateIn(viewModelScope)
    val ttsSpeechRate: StateFlow<Int> = ttsPreferences.speechRate.stateIn(viewModelScope)
    val ttsPitch: StateFlow<Int> = ttsPreferences.pitch.stateIn(viewModelScope)
    val ttsAutoAdvance: StateFlow<Boolean> = ttsPreferences.autoAdvance.stateIn(viewModelScope)

    init {
        viewModelScope.launch {
            val chapter = chapterDao.getByUrl(initialChapterUrl) ?: run {
                _error.value = localizedContext.getString(R.string.error_chapter_not_found)
                _isLoading.value = false
                return@launch
            }
            novelDao.updateLastReadAt(chapter.novelId, System.currentTimeMillis())
            novelDao.getById(chapter.novelId)?.let { novel ->
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
                    loadPages(preloaded = content.pages)
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

    private fun loadPages(preloaded: List<NovelPage>?) {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        flushProgress()
        loadPagesJob?.cancel()
        _pagesChapterId.value = null
        _pages.value = emptyList()
        _isLoading.value = true
        _error.value = null
        loadPagesJob = viewModelScope.launch {
            try {
                // A download may have completed since the chapter list was loaded.
                val fresh = chapterDao.getByUrl(chapter.novelId, chapter.url) ?: chapter
                val cached = chapterDao.getContent(fresh.id)
                val loaded = when {
                    cached != null -> decodeChapterContent(cached)
                        .filter { it.text.isNotBlank() || it.imageUrl != null || it.isSeparator }
                        .map { page ->
                            if (page.imageUrl == null) return@map page
                            val local = chapterImageStore.localImageUri(fresh.novelId, fresh.url, page.index)
                            if (local != null) page.copy(content = PageContent.Image(local)) else page
                        }
                    preloaded != null -> preloaded
                    else -> {
                        extensionManager.awaitReady()
                        currentCoroutineContext().ensureActive()
                        val src = source ?: run {
                            _error.value = localizedContext.getString(R.string.error_source_not_available)
                            return@launch
                        }
                        (src as? PageListSource)?.getPageList(fresh.toChapter())
                            ?: error(localizedContext.getString(R.string.error_no_readable_pages))
                    }
                }
                // Some extensions return normally even after cancellation.
                currentCoroutineContext().ensureActive()
                if (_chapters.value.getOrNull(_currentIndex.value)?.id != chapter.id) return@launch
                _pages.value = loaded
                _pagesChapterId.value = chapter.id
                recordWordCount(fresh, loaded)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (_currentIndex.value > 0) {
            flushProgress()
            _currentIndex.value--
            _chapters.value.getOrNull(_currentIndex.value)?.url?.let { onChapterChanged(it) }
            loadPages()
        }
    }

    fun navigateNext() {
        if (_currentIndex.value < _chapters.value.size - 1) {
            flushProgress()
            val chapter = _chapters.value.getOrNull(_currentIndex.value)
            if (chapter != null && chapter.id == _pagesChapterId.value && !chapter.read) {
                persistenceScope.launch { chapterDao.setRead(chapter.id, true) }
                _chapters.update { list ->
                    list.map { if (it.id == chapter.id) it.copy(read = true, readProgress = 1f) else it }
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
        lastVisibleIndex: Int,
        totalItems: Int,
    ) {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        if (!acceptsReaderProgress(chapter.id, _pagesChapterId.value, chapterId)) return
        val position = ReaderPosition(chapterId, fraction, anchorIndex, anchorOffset)
        livePosition.value = position
        progressWriter.submit(position)
        val shouldMark = shouldMarkReaderChapterRead(
            chapter.read, fraction, lastVisibleIndex, totalItems,
            markAsReadStrategy.value, markAsReadThreshold.value, markAsReadParagraphsFromEnd.value,
        )
        if (shouldMark) {
            _chapters.update { list -> list.map { if (it.id == chapterId) it.copy(read = true) else it } }
            // Read status is prompt; only the frequently changing anchor is debounced.
            persistenceScope.launch { chapterDao.setRead(chapterId, true) }
        }
    }

    fun flushProgress() {
        livePosition.value?.let { position ->
            _chapters.update { list -> list.map { it.withReaderPosition(position) } }
            livePosition.value = null
        }
        progressWriter.flush()
    }

    override fun onCleared() {
        flushProgress()
        // Finish queued writes after viewModelScope is cancelled, then release this scope.
        persistenceJob.complete()
        super.onCleared()
    }

    fun toggleRead() {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        val next = !chapter.read
        viewModelScope.launch {
            chapterDao.setRead(chapter.id, next)
            _chapters.update { list ->
                list.map { if (it.id == chapter.id) it.copy(read = next) else it }
            }
        }
    }

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
                pages = _pages.value,
            )
        }
    }

    fun stopTts() = ttsController.stop()

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
