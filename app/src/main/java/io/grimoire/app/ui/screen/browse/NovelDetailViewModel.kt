package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BROWSE_TTL_MS = 30 * 60 * 1000L

@HiltViewModel
class NovelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
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

    private val _chapterSort = MutableStateFlow(ChapterSort.NUMBER_ASC)
    val chapterSort: StateFlow<ChapterSort> = _chapterSort.asStateFlow()

    private var cachedNovelId: Long = -1L
    private var loadJob: Job? = null

    init {
        loadJob = viewModelScope.launch {
            extensionManager.extensions
                .filter { list -> list.any { it.info.packageName == pkg } }
                .take(1)
                .collect { loadNovel(forceRefresh = false) }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadNovel(forceRefresh = true) }
    }

    fun retryNovel() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val src = source ?: run { _novelError.value = "Source not available"; return@launch }
            fetchFromNetwork(src)
        }
    }

    fun retryChapters() {
        val src = source ?: return
        val novel = _novel.value.takeIf { it.initialized } ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetchChapters(src, novel) }
    }

    private suspend fun loadNovel(forceRefresh: Boolean) {
        val src = source ?: run {
            _novelError.value = "Source not available"
            _isLoadingNovel.value = false
            return
        }

        if (!forceRefresh) {
            val existing = novelDao.getBySourceUrl(src.id, novelUrl)
            if (existing != null && existing.lastUpdated > 0L) {
                val age = System.currentTimeMillis() - existing.lastUpdated
                val fresh = existing.favorite || age < BROWSE_TTL_MS
                if (fresh) {
                    cachedNovelId = existing.id
                    _novel.value = existing.toNovel()
                    _isFavorite.value = existing.favorite
                    _chapterSort.value = ChapterSort.entries.getOrElse(existing.chapterSortOrder) { ChapterSort.NUMBER_ASC }
                    _isLoadingNovel.value = false
                    loadChaptersFromDb(existing.id, src, existing.toNovel())
                    return
                }
            }
        }

        fetchFromNetwork(src)
    }

    private suspend fun loadChaptersFromDb(novelId: Long, src: Source, novel: Novel) {
        _isLoadingChapters.value = true
        _chaptersError.value = null
        val cached = chapterDao.getChaptersOnce(novelId)
        if (cached.isNotEmpty()) {
            _chapters.value = cached.map { it.toChapter() }
            _isLoadingChapters.value = false
        } else {
            fetchChapters(src, novel)
        }
    }

    private suspend fun fetchFromNetwork(src: Source) {
        _isLoadingNovel.value = true
        _novelError.value = null

        val full = runCatching {
            src.getNovelDetails(Novel(url = novelUrl, title = ""))
        }.onSuccess { novel ->
            _novel.value = novel
            val existing = novelDao.getBySourceUrl(src.id, novelUrl)
            val upsertId = novelDao.upsert(novel.toEntity(src.id, existing?.id ?: 0L, existing?.favorite ?: false, existing?.chapterSortOrder ?: 0, novelUrl))
            cachedNovelId = existing?.id ?: upsertId
            _isFavorite.value = existing?.favorite ?: false
            _chapterSort.value = ChapterSort.entries.getOrElse(existing?.chapterSortOrder ?: 0) { ChapterSort.NUMBER_ASC }
        }.onFailure { e ->
            _novelError.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
        }.getOrNull()

        _isLoadingNovel.value = false
        if (full != null) fetchChapters(src, full)
    }

    private suspend fun fetchChapters(src: Source, novel: Novel) {
        _isLoadingChapters.value = true
        _chaptersError.value = null
        _chapterPage.value = 0

        runCatching {
            fetchAllChapters(src, novel)
        }.onSuccess { list ->
            _chapters.value = list
            if (cachedNovelId > 0L) {
                chapterDao.replaceChapters(cachedNovelId, list.map { it.toEntity(cachedNovelId) })
            }
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
            if (new.isEmpty()) break
            all += new
            page++
        }
        return all
    }

    fun setSort(sort: ChapterSort) {
        _chapterSort.value = sort
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateChapterSort(cachedNovelId, sort.ordinal)
        }
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

private fun NovelEntity.toNovel() = Novel(
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    description = description,
    genres = if (genres.isBlank()) emptyList() else genres.split(","),
    status = NovelStatus.entries.getOrElse(status) { NovelStatus.UNKNOWN },
    initialized = true,
)

private fun Novel.toEntity(sourceId: Long, existingId: Long, favorite: Boolean, chapterSortOrder: Int = 0, url: String = this.url) = NovelEntity(
    id = existingId,
    sourceId = sourceId,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    description = description,
    genres = genres.joinToString(","),
    status = status.ordinal,
    favorite = favorite,
    lastUpdated = System.currentTimeMillis(),
    chapterSortOrder = chapterSortOrder,
)

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)

private fun Chapter.toEntity(novelId: Long) = ChapterEntity(
    novelId = novelId,
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
