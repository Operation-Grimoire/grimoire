package io.grimoire.app.ui.screen.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.NovelPage
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.CHAPTER_PAGE_SEPARATOR
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.data.preferences.ReaderPreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    }

    fun loadPages() {
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        val cached = chapter.downloadedContent
        if (cached != null) {
            val pages = cached.split(CHAPTER_PAGE_SEPARATOR)
                .mapIndexed { i, text -> NovelPage(i, text) }
                .filter { it.text.isNotBlank() }
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
