package io.grimoire.app.data.download

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.Chapter
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val extensionManager: ExtensionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isProcessing = AtomicBoolean(false)
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun pause() {
        _isPaused.value = true
    }

    fun resume() {
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun enqueue(chapters: List<ChapterEntity>) {
        scope.launch {
            chapters
                .filter { it.downloadStatus == ChapterDownloadStatus.NONE.ordinal }
                .forEach { chapterDao.setDownloadStatus(it.id, ChapterDownloadStatus.QUEUED.ordinal) }
        }
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun cancel(chapter: ChapterEntity) {
        if (chapter.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal) {
            scope.launch { chapterDao.setDownloadStatus(chapter.id, ChapterDownloadStatus.NONE.ordinal) }
        }
    }

    fun cancelAll(novelId: Long) {
        scope.launch { chapterDao.cancelAllQueued(novelId) }
    }

    fun moveToTopOfQueue(novelId: Long) {
        scope.launch { chapterDao.setQueueOrder(novelId, System.currentTimeMillis()) }
    }

    fun deleteAllDownloads(novelId: Long) {
        scope.launch { chapterDao.deleteAllDownloads(novelId) }
    }

    fun deleteDownload(chapter: ChapterEntity) {
        scope.launch { chapterDao.deleteDownload(chapter.id) }
    }

    suspend fun processQueue(onProgress: (chapterName: String, remaining: Int) -> Unit): Int {
        if (!isProcessing.compareAndSet(false, true)) return 0
        var downloaded = 0
        try {
            chapterDao.resetStuckDownloads()
            while (true) {
                if (_isPaused.value) break
                val chapter = chapterDao.getNextQueued() ?: break
                chapterDao.setDownloadStatus(chapter.id, ChapterDownloadStatus.DOWNLOADING.ordinal)
                onProgress(chapter.name, chapterDao.getQueuedCount())
                runCatching {
                    val novel = novelDao.getById(chapter.novelId) ?: error("Novel not found")
                    val src = extensionManager.extensions.value
                        .firstOrNull { it.source.id == novel.sourceId }?.source
                        ?: error("Source not available")
                    val pages = src.getPageList(chapter.toChapter())
                    val content = pages.joinToString("") { it.text }
                    chapterDao.setDownloadedContent(chapter.id, content, ChapterDownloadStatus.DOWNLOADED.ordinal)
                    downloaded++
                }.onFailure {
                    chapterDao.setDownloadStatus(chapter.id, ChapterDownloadStatus.ERROR.ordinal)
                }
            }
        } finally {
            isProcessing.set(false)
        }
        return downloaded
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)

