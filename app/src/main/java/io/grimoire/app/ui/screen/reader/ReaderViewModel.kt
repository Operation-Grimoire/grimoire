package io.grimoire.app.ui.screen.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.NovelPage
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.decodeChapterContent
import io.grimoire.api.source.SourceInfo
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val readerPreferences: ReaderPreferences,
    private val ttsController: TtsController,
    private val ttsPreferences: TtsPreferences,
    private val chapterImageStore: ChapterImageStore,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])
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

    val currentChapter: StateFlow<ChapterEntity?> = combine(_chapters, _currentIndex) { chapters, idx ->
        chapters.getOrNull(idx)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPrev: StateFlow<Boolean> = combine(_chapters, _currentIndex) { _, idx ->
        idx > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasNext: StateFlow<Boolean> = combine(_chapters, _currentIndex) { chapters, idx ->
        idx >= 0 && idx < chapters.size - 1
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _pages = MutableStateFlow<List<NovelPage>>(emptyList())
    val pages: StateFlow<List<NovelPage>> = _pages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val markAsReadThreshold: StateFlow<Int> = readerPreferences.markAsReadThreshold.stateIn(viewModelScope)
    val fontSize: StateFlow<Int> = readerPreferences.fontSize.stateIn(viewModelScope)
    val lineHeightTimes10: StateFlow<Int> = readerPreferences.lineHeightTimes10.stateIn(viewModelScope)
    val paragraphSpacing: StateFlow<Int> = readerPreferences.paragraphSpacing.stateIn(viewModelScope)
    val readerFont: StateFlow<ReaderFont> = readerPreferences.readerFont.stateIn(viewModelScope)
    val colorTheme: StateFlow<ReaderColorTheme> = readerPreferences.colorTheme.stateIn(viewModelScope)
    val orientation: StateFlow<ReaderOrientation> = readerPreferences.orientation.stateIn(viewModelScope)
    val hideNotificationBar: StateFlow<Boolean> = readerPreferences.hideNotificationBar.stateIn(viewModelScope)
    val hideInlineImages: StateFlow<Boolean> = readerPreferences.hideInlineImages.stateIn(viewModelScope)

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
                _error.value = "Chapter not found in database"
                _isLoading.value = false
                return@launch
            }
            novelDao.updateLastReadAt(chapter.novelId, System.currentTimeMillis())
            val allChapters = chapterDao.getChaptersOnce(chapter.novelId)
            _chapters.value = allChapters
            _currentIndex.value = allChapters.indexOfFirst { it.url == initialChapterUrl }.coerceAtLeast(0)
            loadPages()
        }
        // Keep the reader in sync when TTS auto-advances or skips chapters.
        viewModelScope.launch {
            ttsController.nowPlaying.drop(1).collect { nowPlaying ->
                val url = nowPlaying?.chapterUrl ?: return@collect
                if (ttsController.state.value == TtsPlaybackState.IDLE) return@collect
                val index = _chapters.value.indexOfFirst { it.url == url }
                if (index >= 0 && index != _currentIndex.value) {
                    _currentIndex.value = index
                    loadPages()
                }
            }
        }
    }

    fun loadPages() {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        val cached = chapter.downloadedContent
        if (cached != null) {
            val pages = decodeChapterContent(cached)
                .filter { it.text.isNotBlank() || it.imageUrl != null || it.isSeparator }
                .map { page ->
                    if (page.imageUrl == null) return@map page
                    val local = chapterImageStore.localImageUri(chapter.novelId, chapter.url, page.index)
                    if (local != null) page.copy(imageUrl = local) else page
                }
            _pages.value = pages
            _isLoading.value = false
            _error.value = null
            recordWordCount(chapter, pages)
            return
        }
        val src = source ?: run {
            _error.value = "Source not available"
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _pages.value = emptyList()
            runCatching { src.getPageList(chapter.toChapter()) }
                .onSuccess {
                    _pages.value = it
                    recordWordCount(chapter, it)
                }
                .onFailure { _error.value = "${it::class.simpleName}: ${it.message ?: "(no message)"}" }
            _isLoading.value = false
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
            _currentIndex.value--
            loadPages()
        }
    }

    fun navigateNext() {
        if (_currentIndex.value < _chapters.value.size - 1) {
            val chapter = _chapters.value.getOrNull(_currentIndex.value)
            if (chapter != null && !chapter.read) {
                viewModelScope.launch { chapterDao.setRead(chapter.id, true) }
                _chapters.update { list ->
                    list.map { if (it.id == chapter.id) it.copy(read = true, readProgress = 1f) else it }
                }
            }
            _currentIndex.value++
            loadPages()
        }
    }

    fun updateProgress(fraction: Float) {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        if (chapter.read) return
        val threshold = markAsReadThreshold.value / 100f
        viewModelScope.launch {
            chapterDao.setReadProgress(chapter.id, fraction)
            if (fraction >= threshold) {
                chapterDao.setRead(chapter.id, true)
                _chapters.update { list ->
                    list.map { if (it.id == chapter.id) it.copy(read = true, readProgress = 1f) else it }
                }
            } else {
                _chapters.update { list ->
                    list.map { if (it.id == chapter.id) it.copy(readProgress = fraction) else it }
                }
            }
        }
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
