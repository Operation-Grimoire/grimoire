package io.grimoire.app.ui.screen.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.api.source.EpubSource
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.epub.EpubImporter
import io.grimoire.app.data.epub.LOCAL_PKG
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
    private val categoryDao: CategoryDao,
    private val downloadManager: DownloadManager,
    private val epubImporter: EpubImporter,
    private val novelUpdatesRepository: NovelUpdatesInfoRepository,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])
    private val novelUrl: String = checkNotNull(savedStateHandle["url"])

    /** A locally-imported EPUB novel: fully stored in the DB, no backing extension. */
    val isLocal: Boolean = pkg == LOCAL_PKG

    private val loaded get() = extensionManager.extensions.value.firstOrNull { it.info.packageName == pkg }
    private val source get() = loaded?.source

    val sourceName: String get() = loaded?.info?.label ?: ""

    /** This source delivers a whole-book EPUB rather than scraped chapters. */
    val isEpubSource: Boolean get() = source is EpubSource

    private val _bookDownload = MutableStateFlow<BookDownloadState>(BookDownloadState.Idle)
    val bookDownload: StateFlow<BookDownloadState> = _bookDownload.asStateFlow()

    val novelWebUrl: String get() {
        val url = _novel.value.url
        if (url.startsWith("http")) return url
        val baseUrl = loaded?.source?.javaClass?.getAnnotation(SourceInfo::class.java)?.baseUrl ?: return url
        return "$baseUrl$url"
    }

    private val _novel = MutableStateFlow(Novel(url = novelUrl, title = ""))
    val novel: StateFlow<Novel> = _novel.asStateFlow()

    private val _liveNovelId = MutableStateFlow(-1L)
    @OptIn(FlowPreview::class)
    val chapters: StateFlow<List<ChapterEntity>> = _liveNovelId
        .flatMapLatest { id -> if (id > 0L) chapterDao.getChapters(id) else flowOf(emptyList()) }
        .debounce(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _categoryId = MutableStateFlow<Long?>(null)
    val categoryId: StateFlow<Long?> = _categoryId.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _nuState = MutableStateFlow<NuInfoState>(NuInfoState.Idle)
    val nuState: StateFlow<NuInfoState> = _nuState.asStateFlow()

    private val _nuSearchResults = MutableStateFlow<List<NuSearchResult>>(emptyList())
    val nuSearchResults: StateFlow<List<NuSearchResult>> = _nuSearchResults.asStateFlow()

    private val _nuSearching = MutableStateFlow(false)
    val nuSearching: StateFlow<Boolean> = _nuSearching.asStateFlow()

    private var cachedNovelId: Long = -1L
    private var loadJob: Job? = null
    private var nuJob: Job? = null

    init {
        loadJob = viewModelScope.launch {
            if (isLocal) {
                loadLocalNovel()
            } else {
                extensionManager.extensions
                    .filter { list -> list.any { it.info.packageName == pkg } }
                    .take(1)
                    .collect { loadNovel(forceRefresh = false) }
            }
        }
        viewModelScope.launch {
            // Don't fetch automatically — just decide whether to offer the
            // "Load from NovelUpdates" button (cheap preference read, no network).
            _nuState.value = if (novelUpdatesRepository.isEnabled()) {
                NuInfoState.NotLoaded
            } else {
                NuInfoState.Disabled
            }
        }
    }

    private fun loadNovelUpdates(title: String) {
        nuJob?.cancel()
        nuJob = viewModelScope.launch {
            _nuState.value = NuInfoState.Loading
            _nuState.value = novelUpdatesRepository.infoFor(pkg, novelUrl, title)
        }
    }

    /** Triggered by the user tapping the "Load from NovelUpdates" button. */
    fun loadNovelUpdates() = retryNovelUpdates()

    fun retryNovelUpdates() {
        val title = _novel.value.title
        if (title.isNotBlank()) loadNovelUpdates(title)
    }

    fun linkNovelUpdates(slug: String) {
        nuJob?.cancel()
        nuJob = viewModelScope.launch {
            _nuState.value = NuInfoState.Loading
            _nuState.value = novelUpdatesRepository.link(pkg, novelUrl, slug)
        }
    }

    fun searchNovelUpdates(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _nuSearching.value = true
            _nuSearchResults.value = novelUpdatesRepository.search(query)
            _nuSearching.value = false
        }
    }

    private suspend fun loadLocalNovel() {
        val existing = novelDao.getBySourceUrl(LOCAL_SOURCE_ID, novelUrl) ?: run {
            _novelError.value = "Imported book not found"
            _isLoadingNovel.value = false
            return
        }
        cachedNovelId = existing.id
        _liveNovelId.value = existing.id
        _novel.value = existing.toNovel()
        _isFavorite.value = existing.favorite
        _chapterSort.value = ChapterSort.entries.getOrElse(existing.chapterSortOrder) { ChapterSort.NUMBER_ASC }
        _categoryId.value = existing.categoryId
        _isLoadingNovel.value = false
        _isLoadingChapters.value = false
    }

    fun refresh() {
        if (isLocal) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadNovel(forceRefresh = true) }
    }

    fun retryNovel() {
        if (isLocal) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val src = source ?: run { _novelError.value = "Source not available"; return@launch }
            fetchFromNetwork(src)
        }
    }

    fun retryChapters() {
        if (isLocal) return
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
                    _liveNovelId.value = existing.id
                    _novel.value = existing.toNovel()
                    _isFavorite.value = existing.favorite
                    _chapterSort.value = ChapterSort.entries.getOrElse(existing.chapterSortOrder) { ChapterSort.NUMBER_ASC }
                    _categoryId.value = existing.categoryId
                    _isLoadingNovel.value = false
                    loadChaptersFromDb(existing.id, src, existing.toNovel())
                    return
                }
            }
        }

        fetchFromNetwork(src)
    }

    private suspend fun loadChaptersFromDb(novelId: Long, src: Source, novel: Novel) {
        _liveNovelId.value = novelId
        _isLoadingChapters.value = true
        _chaptersError.value = null
        val cached = chapterDao.getChaptersOnce(novelId)
        if (cached.isNotEmpty()) {
            // Wait for the debounced StateFlow to actually emit before hiding skeleton,
            // so there's no flash of empty chapter list.
            chapters.first { it.isNotEmpty() }
            _isLoadingChapters.value = false
        } else if (src is EpubSource) {
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
            val upsertId = novelDao.upsert(novel.toEntity(src.id, existing?.id ?: 0L, existing?.favorite ?: false, existing?.chapterSortOrder ?: 0, existing?.categoryId, novelUrl, existing?.lastReadAt ?: 0L))
            cachedNovelId = existing?.id ?: upsertId
            _liveNovelId.value = cachedNovelId
            _isFavorite.value = existing?.favorite ?: false
            _chapterSort.value = ChapterSort.entries.getOrElse(existing?.chapterSortOrder ?: 0) { ChapterSort.NUMBER_ASC }
            _categoryId.value = existing?.categoryId
        }.onFailure { e ->
            _novelError.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
        }.getOrNull()

        _isLoadingNovel.value = false
        // EPUB sources have no scraped chapter list; chapters appear after the
        // user downloads the book (see downloadBook()).
        if (full != null && src !is EpubSource) fetchChapters(src, full)
    }

    private suspend fun fetchChapters(src: Source, novel: Novel) {
        _isLoadingChapters.value = true
        _chaptersError.value = null
        _chapterPage.value = 0

        runCatching {
            fetchAllChapters(src, novel)
        }.onSuccess { list ->
            if (cachedNovelId > 0L) {
                val existing = chapterDao.getChaptersOnce(cachedNovelId).associateBy { it.url }
                chapterDao.replaceChapters(cachedNovelId, list.map { ch ->
                    val prev = existing[ch.url]
                    ch.toEntity(cachedNovelId).copy(
                        read = prev?.read ?: false,
                        readProgress = prev?.readProgress ?: 0f,
                        downloadStatus = prev?.downloadStatus ?: 0,
                        downloadedContent = prev?.downloadedContent,
                    )
                })
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

    fun markChapterRead(chapter: ChapterEntity, read: Boolean) = viewModelScope.launch {
        chapterDao.setRead(chapter.id, read)
    }

    fun markAllRead(read: Boolean) {
        if (cachedNovelId <= 0L) return
        viewModelScope.launch { chapterDao.markAllRead(cachedNovelId, read) }
    }

    fun markChaptersRead(ids: List<Long>, read: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.chunked(999).forEach { chunk -> chapterDao.markChapters(chunk, read) }
        }
    }

    fun setCategory(categoryId: Long?) {
        _categoryId.value = categoryId
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateCategory(cachedNovelId, categoryId)
        }
    }

    fun downloadChapter(chapter: ChapterEntity) = downloadManager.enqueue(listOf(chapter))
    fun downloadAll() = downloadManager.enqueue(chapters.value)
    fun downloadUnread() = downloadManager.enqueue(chapters.value.filter { !it.read })
    fun cancelDownload(chapter: ChapterEntity) = downloadManager.cancel(chapter)
    fun cancelAllDownloads() { if (cachedNovelId > 0L) downloadManager.cancelAll(cachedNovelId) }
    fun deleteDownload(chapter: ChapterEntity) = downloadManager.deleteDownload(chapter)

    /** Downloads and imports the whole-book EPUB for an [EpubSource]. */
    fun downloadBook() {
        val src = source as? EpubSource ?: run {
            _bookDownload.value = BookDownloadState.Error("Source unavailable")
            return
        }
        if (_bookDownload.value is BookDownloadState.Downloading) return
        _bookDownload.value = BookDownloadState.Downloading
        viewModelScope.launch {
            runCatching { src.getEpub(_novel.value) }
                .mapCatching { bytes -> epubImporter.importBytes(bytes, src.id, novelUrl).getOrThrow() }
                .onSuccess { result ->
                    cachedNovelId = result.novelId
                    _liveNovelId.value = result.novelId
                    _bookDownload.value = BookDownloadState.Done
                }
                .onFailure { e ->
                    _bookDownload.value = BookDownloadState.Error(
                        e.message ?: e::class.simpleName ?: "Download failed",
                    )
                }
        }
    }

    fun setSort(sort: ChapterSort) {
        _chapterSort.value = sort
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateChapterSort(cachedNovelId, sort.ordinal)
        }
    }

    fun toggleFavorite() {
        val sourceId = if (isLocal) LOCAL_SOURCE_ID else (source ?: return).id
        val next = !_isFavorite.value
        _isFavorite.value = next
        viewModelScope.launch {
            val entity = novelDao.getBySourceUrl(sourceId, novelUrl) ?: return@launch
            novelDao.upsert(entity.copy(favorite = next))
        }
    }
}

sealed interface BookDownloadState {
    data object Idle : BookDownloadState
    data object Downloading : BookDownloadState
    data object Done : BookDownloadState
    data class Error(val message: String) : BookDownloadState
}

private fun NovelEntity.toNovel() = Novel(
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    description = description,
    genres = if (genres.isBlank()) emptyList() else genres.split(","),
    status = NovelStatus.entries.getOrElse(status) { NovelStatus.UNKNOWN },
    rating = rating,
    ratingCount = ratingCount,
    language = language,
    initialized = true,
)

private fun Novel.toEntity(sourceId: Long, existingId: Long, favorite: Boolean, chapterSortOrder: Int = 0, categoryId: Long? = null, url: String = this.url, lastReadAt: Long = 0L) = NovelEntity(
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
    categoryId = categoryId,
    lastReadAt = lastReadAt,
    rating = rating,
    ratingCount = ratingCount,
    language = language,
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
