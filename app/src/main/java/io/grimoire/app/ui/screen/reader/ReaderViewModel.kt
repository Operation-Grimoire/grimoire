package io.grimoire.app.ui.screen.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.NovelPage
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.entity.ChapterEntity
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
    readerPreferences: ReaderPreferences,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])
    private val initialChapterUrl: String = checkNotNull(savedStateHandle["chapterUrl"])

    private val source get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }?.source

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

    init {
        viewModelScope.launch {
            val chapter = chapterDao.getByUrl(initialChapterUrl) ?: run {
                _error.value = "Chapter not found in database"
                _isLoading.value = false
                return@launch
            }
            val allChapters = chapterDao.getChaptersOnce(chapter.novelId)
            _chapters.value = allChapters
            _currentIndex.value = allChapters.indexOfFirst { it.url == initialChapterUrl }.coerceAtLeast(0)
            loadPages()
        }
    }

    fun loadPages() {
        val src = source ?: run {
            _error.value = "Source not available"
            _isLoading.value = false
            return
        }
        val chapter = _chapters.value.getOrNull(_currentIndex.value) ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _pages.value = emptyList()
            runCatching { src.getPageList(chapter.toChapter()) }
                .onSuccess { _pages.value = it }
                .onFailure { _error.value = "${it::class.simpleName}: ${it.message ?: "(no message)"}" }
            _isLoading.value = false
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
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
