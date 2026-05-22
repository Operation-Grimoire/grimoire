package io.grimoire.app.data.download

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.Chapter
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.encodeChapterContent
import io.grimoire.app.data.preferences.DownloadPreferences
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val extensionManager: ExtensionManager,
    private val downloadPreferences: DownloadPreferences,
    private val chapterImageStore: ChapterImageStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isProcessing = AtomicBoolean(false)
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    val concurrency: StateFlow<Int> = downloadPreferences.concurrency
        .changes()
        .stateIn(scope, SharingStarted.Eagerly, downloadPreferences.concurrency.defaultValue())

    fun pause() {
        _isPaused.value = true
    }

    fun resume() {
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun setConcurrency(value: Int) {
        scope.launch { downloadPreferences.concurrency.set(value.coerceIn(1, 5)) }
    }

    fun enqueue(chapters: List<ChapterEntity>) {
        scope.launch {
            val ids = chapters
                .filter {
                    it.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
                        it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal
                }
                .map { it.id }
            ids.chunked(999).forEach { chunk ->
                chapterDao.setDownloadStatusBatch(chunk, ChapterDownloadStatus.QUEUED.ordinal)
            }
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

    /** Clears every queued chapter download; any in-flight downloads finish on their own. */
    fun cancelAll() {
        scope.launch { chapterDao.cancelAllQueuedDownloads() }
    }

    fun moveToTopOfQueue(novelId: Long) {
        scope.launch { chapterDao.setQueueOrder(novelId, System.currentTimeMillis()) }
    }

    fun deleteAllDownloads(novelId: Long) {
        scope.launch {
            chapterDao.deleteAllDownloads(novelId)
            chapterImageStore.deleteNovel(novelId)
        }
    }

    fun deleteDownload(chapter: ChapterEntity) {
        scope.launch {
            chapterDao.deleteDownload(chapter.id)
            chapterImageStore.deleteChapter(chapter.novelId, chapter.url)
        }
    }

    fun retryChapter(chapter: ChapterEntity) {
        if (chapter.downloadStatus != ChapterDownloadStatus.ERROR.ordinal) return
        scope.launch { chapterDao.setDownloadStatus(chapter.id, ChapterDownloadStatus.QUEUED.ordinal) }
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun retryAll(novelId: Long) {
        scope.launch { chapterDao.retryAllFailed(novelId) }
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    suspend fun processQueue(onProgress: (chapterName: String, remaining: Int) -> Unit): Int {
        if (!isProcessing.compareAndSet(false, true)) return -1
        val n = concurrency.value.coerceIn(1, 5)
        val downloaded = AtomicInteger(0)
        val mutex = Mutex()
        try {
            chapterDao.resetStuckDownloads()
            coroutineScope {
                repeat(n) {
                    launch {
                        while (!_isPaused.value) {
                            val chapter = mutex.withLock {
                                val ch = chapterDao.getNextQueued() ?: return@withLock null
                                chapterDao.setDownloadStatus(ch.id, ChapterDownloadStatus.DOWNLOADING.ordinal)
                                ch
                            } ?: break
                            runCatching {
                                val novel = novelDao.getById(chapter.novelId) ?: error("Novel not found")
                                val src = extensionManager.extensions.value
                                    .firstOrNull { it.source.id == novel.sourceId }?.source
                                    ?: error("Source not available")
                                val pages = src.getPageList(chapter.toChapter())
                                val content = encodeChapterContent(pages)
                                chapterDao.setDownloadedContent(chapter.id, content, ChapterDownloadStatus.DOWNLOADED.ordinal)
                                // Best-effort: text is already saved, so a failed image
                                // fetch must not flip the chapter to ERROR.
                                runCatching {
                                    chapterImageStore.saveImages(chapter.novelId, chapter.url, pages)
                                }
                                downloaded.incrementAndGet()
                            }.onFailure {
                                chapterDao.setDownloadStatus(chapter.id, ChapterDownloadStatus.ERROR.ordinal)
                            }
                            onProgress(chapter.name, chapterDao.getQueuedCount())
                        }
                    }
                }
            }
        } finally {
            isProcessing.set(false)
        }
        return downloaded.get()
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
