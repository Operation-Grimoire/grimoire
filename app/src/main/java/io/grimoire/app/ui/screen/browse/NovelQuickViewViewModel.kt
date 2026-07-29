package io.grimoire.app.ui.screen.browse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.epub.EpubSource
import io.grimoire.api.source.Source
import io.grimoire.api.source.sourceIdFor
import io.grimoire.app.R
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.source.fetchAllChapters
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backing VM for the long-press preview sheet; warms the same DB cache the full detail screen uses. */
@HiltViewModel(assistedFactory = NovelQuickViewViewModel.Factory::class)
class NovelQuickViewViewModel @AssistedInject constructor(
    @ApplicationContext context: Context,
    @Assisted("pkg") val pkg: String,
    @Assisted("novelUrl") private val novelUrl: String,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val browsingHistoryDao: io.grimoire.app.data.local.dao.BrowsingHistoryDao,
    private val categoryDao: CategoryDao,
    private val downloadManager: DownloadManager,
    private val authManager: HiddenCategoriesAuthManager,
    private val analytics: io.grimoire.app.data.analytics.Analytics,
) : ViewModel() {

    /** Resources in the in-app UI language, for error text surfaced to the screen. */
    private val localizedContext = AppLocale.wrap(context)

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("pkg") pkg: String,
            @Assisted("novelUrl") novelUrl: String,
        ): NovelQuickViewViewModel
    }

    private val loaded get() = extensionManager.extensions.value.firstOrNull { it.info.packageName == pkg }
    private val source get() = loaded?.source

    /** Canonical id this novel is keyed by — derived from [pkg], not [Source.id]. */
    private val canonicalSourceId: Long get() = sourceIdFor(pkg)

    val sourceName: String get() = loaded?.info?.label ?: ""

    private val _novel = MutableStateFlow(Novel(url = novelUrl, title = "", language = Language.UNKNOWN))
    val novel: StateFlow<Novel> = _novel.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _categoryId = MutableStateFlow<Long?>(null)
    val categoryId: StateFlow<Long?> = _categoryId.asStateFlow()

    private val _liveNovelId = MutableStateFlow(-1L)

    @OptIn(FlowPreview::class)
    val chapters: StateFlow<List<ChapterEntity>> = _liveNovelId
        .flatMapLatest { id -> if (id > 0L) chapterDao.getChapters(id) else flowOf(emptyList()) }
        .debounce(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chapterCount: StateFlow<Int> = chapters
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val latestChapters: StateFlow<List<ChapterEntity>> = chapters
        .map { list -> list.sortedByDescending { it.uploadDate.takeIf { d -> d > 0L } ?: it.id }.take(LATEST_PREVIEW_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        combine(allCategories, authManager.isUnlocked) { list, unlocked ->
            if (unlocked) list else list.filter { !it.isHidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val biometricEnabled: StateFlow<Boolean> = authManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True when an unlock prompt would be useful: locked, a PIN is set, and at least one category is hidden. */
    val canUnlockHidden: StateFlow<Boolean> = combine(
        authManager.isUnlocked,
        authManager.hasPin,
        allCategories,
    ) { unlocked, hasPin, all -> !unlocked && hasPin && all.any { it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    suspend fun verifyAndUnlock(pin: String): Boolean {
        val ok = authManager.verifyPin(pin)
        if (ok) authManager.unlock()
        return ok
    }

    fun unlockFromBiometric() = authManager.unlock()

    private var cachedNovelId: Long = -1L

    init {
        viewModelScope.launch { loadNovel() }
    }

    private suspend fun loadNovel() {
        val src = source ?: run {
            _error.value = localizedContext.getString(R.string.error_source_not_available)
            _isLoading.value = false
            return
        }

        val existing = novelDao.getBySourceUrl(canonicalSourceId, novelUrl)
        if (existing != null && existing.lastUpdated > 0L) {
            val age = System.currentTimeMillis() - existing.lastUpdated
            val fresh = existing.favorite || age < BROWSE_TTL_MS
            if (fresh) {
                // Re-opening a cached novel extends its prune grace window.
                if (!existing.favorite) novelDao.touchAccessed(existing.id, System.currentTimeMillis())
                cachedNovelId = existing.id
                _liveNovelId.value = existing.id
                _novel.value = existing.toNovel()
                _isFavorite.value = existing.favorite
                _categoryId.value = existing.categoryId
                _isLoading.value = false
                return
            }
        }

        _isLoading.value = true
        _error.value = null
        runCatching { src.getNovelDetails(Novel(url = novelUrl, title = "", language = Language.UNKNOWN)) }
            .onSuccess { novel ->
                _novel.value = novel
                val current = novelDao.getBySourceUrl(canonicalSourceId, novelUrl)
                val upsertId = novelDao.upsert(
                    novel.toEntity(
                        sourceId = canonicalSourceId,
                        existingId = current?.id ?: 0L,
                        favorite = current?.favorite ?: false,
                        chapterSortOrder = current?.chapterSortOrder ?: 0,
                        categoryId = current?.categoryId,
                        url = novelUrl,
                        lastReadAt = current?.lastReadAt ?: 0L,
                        notifyOnNewChapters = current?.notifyOnNewChapters ?: false,
                        notifyOnNewLockedChapters = current?.notifyOnNewLockedChapters ?: false,
                        autoDownloadNewChapters = current?.autoDownloadNewChapters ?: false,
                        customCoverPath = current?.customCoverPath,
                        customCoverUrl = current?.customCoverUrl,
                        overrideTitle = current?.overrideTitle,
                        overrideAuthor = current?.overrideAuthor,
                        overrideDescription = current?.overrideDescription,
                        overrideStatus = current?.overrideStatus,
                        overrideGenres = current?.overrideGenres,
                    )
                )
                cachedNovelId = current?.id ?: upsertId
                _liveNovelId.value = cachedNovelId
                _isFavorite.value = current?.favorite ?: false
                _categoryId.value = current?.categoryId
                _isLoading.value = false
                if (src !is EpubSource) {
                    _isLoadingChapters.value = true
                    fetchChapters(src, novel)
                    _isLoadingChapters.value = false
                }
            }
            .onFailure { e ->
                _error.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
                _isLoading.value = false
            }
    }

    private suspend fun fetchChapters(src: Source, novel: Novel) {
        runCatching { fetchAllChapters(src, novel) }
            .onSuccess { list ->
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
            }
    }

    fun toggleFavorite() {
        val src = source ?: return
        val next = !_isFavorite.value
        _isFavorite.value = next
        if (next) trackNovelAdded()
        viewModelScope.launch {
            val entity = novelDao.getBySourceUrl(canonicalSourceId, novelUrl) ?: return@launch
            novelDao.upsert(entity.copy(favorite = next))
            // Browsing history is only for non-library novels: drop the row once added.
            if (next) browsingHistoryDao.deleteByNovel(pkg, novelUrl)
        }
    }

    private fun trackNovelAdded() = loaded?.let {
        analytics.trackSource(
            io.grimoire.app.data.analytics.AnalyticsEvent.NOVEL_ADDED,
            it.id,
            it.info.label,
        )
    }

    fun setCategory(target: Long?) {
        val src = source ?: return
        _categoryId.value = target
        if (!_isFavorite.value) {
            _isFavorite.value = true
            trackNovelAdded()
        }
        viewModelScope.launch {
            val entity = novelDao.getBySourceUrl(canonicalSourceId, novelUrl) ?: return@launch
            novelDao.upsert(entity.copy(categoryId = target, favorite = true))
            browsingHistoryDao.deleteByNovel(pkg, novelUrl)
        }
    }

    fun downloadChapter(chapter: ChapterEntity) = downloadManager.enqueue(listOf(chapter))
    fun cancelDownload(chapter: ChapterEntity) = downloadManager.cancel(chapter)
    fun deleteDownload(chapter: ChapterEntity) = downloadManager.deleteDownload(chapter)
    fun redownloadChapter(chapter: ChapterEntity) =
        downloadManager.enqueue(listOf(chapter), force = true)

    companion object {
        const val LATEST_PREVIEW_LIMIT = 5
    }
}
