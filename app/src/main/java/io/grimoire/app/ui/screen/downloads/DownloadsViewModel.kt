package io.grimoire.app.ui.screen.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal data class NovelDownloads(
    val novel: NovelEntity,
    val chapters: List<ChapterEntity>,
    val counts: DownloadCounts = DownloadCounts(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val downloadManager: DownloadManager,
    authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    private val statusFilters = MutableStateFlow<Set<DownloadStatusFilter>>(emptySet())
    internal val activeStatusFilters: StateFlow<Set<DownloadStatusFilter>> = statusFilters.asStateFlow()

    // Grouped + sorted downloads with per-novel counts, before the status filter is applied.
    // The novel lookup is a suspend batch query; mapLatest cancels a stale grouping if the
    // chapter list re-emits (e.g. a progress tick) before the previous one finished.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseDownloads = authManager.isUnlocked.map { !it }.distinctUntilChanged()
        .flatMapLatest { excludeHidden -> chapterDao.getAllDownloads(excludeHidden) }
        .mapLatest { chapters ->
            val novelIds = chapters.mapTo(mutableSetOf()) { it.novelId }
            val novelById = if (novelIds.isEmpty()) emptyMap()
            else novelDao.getByIds(novelIds.toList()).associateBy { it.id }
            groupDownloads(chapters, novelById)
        }

    // The list the screen renders: grouped, sorted, and already narrowed to the active filter.
    // All of it (grouping, sorting, counting, filtering) runs on Dispatchers.Default so the UI
    // thread never does the projection — the screen just reads precomputed sections by index.
    internal val downloads: StateFlow<List<NovelDownloads>?> =
        combine(baseDownloads, statusFilters) { grouped, filters ->
            applyStatusFilter(grouped, filters)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPaused = downloadManager.isPaused
    val concurrency = downloadManager.concurrency

    /** Toggle a status filter chip; the projection re-narrows off the main thread. */
    internal fun toggleStatusFilter(filter: DownloadStatusFilter) =
        statusFilters.update { if (filter in it) it - filter else it + filter }

    /** Clear all status filters (the "All" chip). */
    fun clearStatusFilters() {
        statusFilters.value = emptySet()
    }

    fun togglePause() {
        if (downloadManager.isPaused.value) downloadManager.resume() else downloadManager.pause()
    }

    fun setConcurrency(value: Int) = downloadManager.setConcurrency(value)
    fun retryChapter(chapter: ChapterEntity) = downloadManager.retryChapter(chapter)
    fun retryAll(novelId: Long) = downloadManager.retryAll(novelId)
    fun cancelAll(novelId: Long) = downloadManager.cancelAll(novelId)
    fun cancelAllFailed(novelId: Long) = downloadManager.cancelAllFailed(novelId)
    fun moveToTopOfQueue(novelId: Long) = downloadManager.moveToTopOfQueue(novelId)
    fun deleteAllDownloads(novelId: Long) = downloadManager.deleteAllDownloads(novelId)
    fun cancel(chapter: ChapterEntity) = downloadManager.cancel(chapter)
    fun deleteDownload(chapter: ChapterEntity) = downloadManager.deleteDownload(chapter)
    fun redownloadChapter(chapter: ChapterEntity) =
        downloadManager.enqueue(listOf(chapter), force = true)
    fun redownloadChapters(chapters: List<ChapterEntity>) =
        downloadManager.enqueue(chapters.filter { !it.locked }, force = true)
}
