package io.grimoire.app.ui.screen.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private val STATUS_ORDER = mapOf(
    ChapterDownloadStatus.DOWNLOADING.ordinal to 0,
    ChapterDownloadStatus.QUEUED.ordinal to 1,
    ChapterDownloadStatus.DOWNLOADED.ordinal to 2,
    ChapterDownloadStatus.ERROR.ordinal to 3,
)

data class NovelDownloads(
    val novel: NovelEntity,
    val chapters: List<ChapterEntity>,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val downloads = chapterDao.getAllDownloads()
        .flatMapLatest { chapters ->
            flow {
                val groups = chapters.groupBy { it.novelId }
                val novelById = if (groups.isEmpty()) emptyMap()
                else novelDao.getByIds(groups.keys.toList()).associateBy { it.id }
                val result = groups.mapNotNull { (novelId, chs) ->
                    val novel = novelById[novelId] ?: return@mapNotNull null
                    val sorted = chs.sortedWith(
                        compareBy({ STATUS_ORDER[it.downloadStatus] ?: 4 }, { it.chapterNumber })
                    )
                    NovelDownloads(novel, sorted)
                }.sortedByDescending { nd ->
                    nd.chapters.any {
                        it.downloadStatus == ChapterDownloadStatus.DOWNLOADING.ordinal ||
                            it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal
                    }
                }
                emit(result)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPaused = downloadManager.isPaused
    val concurrency = downloadManager.concurrency

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
}
