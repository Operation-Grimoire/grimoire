package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NovelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])
    private val novelUrl: String = checkNotNull(savedStateHandle["url"])

    private val source get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }?.source

    private val _novel = MutableStateFlow(Novel(url = novelUrl, title = ""))
    val novel: StateFlow<Novel> = _novel.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _isLoadingNovel = MutableStateFlow(true)
    val isLoadingNovel: StateFlow<Boolean> = _isLoadingNovel.asStateFlow()

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters.asStateFlow()

    private val _novelError = MutableStateFlow<String?>(null)
    val novelError: StateFlow<String?> = _novelError.asStateFlow()

    private val _chaptersError = MutableStateFlow<String?>(null)
    val chaptersError: StateFlow<String?> = _chaptersError.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _chapterPage = MutableStateFlow(0)
    val chapterPage: StateFlow<Int> = _chapterPage.asStateFlow()

    init {
        viewModelScope.launch {
            extensionManager.extensions
                .filter { list -> list.any { it.info.packageName == pkg } }
                .take(1)
                .collect { fetchNovel() }
        }
    }

    fun fetchNovel() {
        val src = source ?: run { _novelError.value = "Source not available"; _isLoadingNovel.value = false; return }
        viewModelScope.launch {
            _isLoadingNovel.value = true
            _novelError.value = null

            val full = runCatching {
                src.getNovelDetails(Novel(url = novelUrl, title = ""))
            }.onSuccess { novel ->
                _novel.value = novel
                val existing = novelDao.getBySourceUrl(src.id, novelUrl)
                novelDao.upsert(
                    NovelEntity(
                        id = existing?.id ?: 0L,
                        sourceId = src.id,
                        url = novel.url,
                        title = novel.title,
                        thumbnailUrl = novel.thumbnailUrl,
                        description = novel.description,
                        status = novel.status.ordinal,
                        favorite = existing?.favorite ?: false,
                        lastUpdated = System.currentTimeMillis(),
                    )
                )
                _isFavorite.value = existing?.favorite ?: false
            }.onFailure { e ->
                _novelError.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
            }.getOrNull()

            _isLoadingNovel.value = false

            if (full != null) fetchChapters(src, full)
        }
    }

    fun fetchChapters() {
        val src = source ?: return
        val novel = _novel.value.takeIf { it.initialized } ?: return
        viewModelScope.launch { fetchChapters(src, novel) }
    }

    private suspend fun fetchChapters(src: Source, novel: Novel) {
        _isLoadingChapters.value = true
        _chaptersError.value = null
        _chapterPage.value = 0

        runCatching {
            fetchAllChapters(src, novel)
        }.onSuccess { list ->
            _chapters.value = list
        }.onFailure { e ->
            _chaptersError.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
        }

        _isLoadingChapters.value = false
        _chapterPage.value = 0
    }

    private suspend fun fetchAllChapters(src: Source, novel: Novel): List<Chapter> {
        if (src !is PaginatedSource) return src.getChapterList(novel)

        val all = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        var page = 1
        while (true) {
            _chapterPage.value = page
            val batch = src.getChapterList(novel, page)
            if (batch.isEmpty()) break
            val new = batch.filter { seen.add(it.url) }
            if (new.isEmpty()) break  // redirect or repeat — past last page
            all += new
            page++
        }
        return all
    }

    fun toggleFavorite() {
        val src = source ?: return
        val next = !_isFavorite.value
        _isFavorite.value = next
        viewModelScope.launch {
            val entity = novelDao.getBySourceUrl(src.id, novelUrl) ?: return@launch
            novelDao.upsert(entity.copy(favorite = next))
        }
    }
}
