package io.grimoire.app.data.download

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.Chapter
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.encodeChapterContent
import io.grimoire.app.data.preferences.DownloadPreferences
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
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
    private val categoryDao: CategoryDao,
    private val extensionManager: ExtensionManager,
    private val downloadPreferences: DownloadPreferences,
    private val chapterImageStore: ChapterImageStore,
    private val authManager: HiddenCategoriesAuthManager,
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

    fun enqueue(chapters: List<ChapterEntity>, force: Boolean = false) {
        scope.launch {
            // NONE / ERROR rows become a fresh QUEUED; DOWNLOADED / REDOWNLOAD_ERROR (only
            // when force=true) become REDOWNLOAD_QUEUED so the row keeps reading as
            // downloaded throughout the refresh.
            val freshIds = mutableListOf<Long>()
            val refreshIds = mutableListOf<Long>()
            for (ch in chapters) {
                if (ch.locked) continue
                when (ch.downloadStatus) {
                    ChapterDownloadStatus.NONE.ordinal,
                    ChapterDownloadStatus.ERROR.ordinal -> freshIds += ch.id
                    ChapterDownloadStatus.DOWNLOADED.ordinal,
                    ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal -> if (force) refreshIds += ch.id
                }
            }
            freshIds.chunked(999).forEach { chunk ->
                chapterDao.setDownloadStatusBatch(chunk, ChapterDownloadStatus.QUEUED.ordinal)
            }
            refreshIds.chunked(999).forEach { chunk ->
                chapterDao.setDownloadStatusBatch(chunk, ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal)
            }
        }
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun cancel(chapter: ChapterEntity) {
        val restoredStatus = when (chapter.downloadStatus) {
            ChapterDownloadStatus.QUEUED.ordinal -> ChapterDownloadStatus.NONE.ordinal
            ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal -> ChapterDownloadStatus.DOWNLOADED.ordinal
            else -> return
        }
        scope.launch { chapterDao.setDownloadStatus(chapter.id, restoredStatus) }
    }

    fun cancelDownloads(chapters: List<ChapterEntity>) {
        scope.launch {
            val freshIds = chapters
                .filter { it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal }
                .map { it.id }
            val refreshIds = chapters
                .filter { it.downloadStatus == ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal }
                .map { it.id }
            freshIds.chunked(999).forEach { chunk ->
                chapterDao.setDownloadStatusBatch(chunk, ChapterDownloadStatus.NONE.ordinal)
            }
            refreshIds.chunked(999).forEach { chunk ->
                chapterDao.setDownloadStatusBatch(chunk, ChapterDownloadStatus.DOWNLOADED.ordinal)
            }
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

    fun deleteDownloads(chapters: List<ChapterEntity>) {
        scope.launch {
            chapters.forEach { chapter ->
                chapterDao.deleteDownload(chapter.id)
                chapterImageStore.deleteChapter(chapter.novelId, chapter.url)
            }
        }
    }

    fun retryChapter(chapter: ChapterEntity) {
        val target = when (chapter.downloadStatus) {
            ChapterDownloadStatus.ERROR.ordinal -> ChapterDownloadStatus.QUEUED.ordinal
            ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal -> ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal
            else -> return
        }
        scope.launch { chapterDao.setDownloadStatus(chapter.id, target) }
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun retryAll(novelId: Long) {
        scope.launch { chapterDao.retryAllFailed(novelId) }
        _isPaused.value = false
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun cancelAllFailed(novelId: Long) {
        scope.launch { chapterDao.cancelAllFailed(novelId) }
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
                            val picked = mutex.withLock {
                                val ch = chapterDao.getNextQueued() ?: return@withLock null
                                val isRefresh = ch.downloadStatus == ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal
                                val inflight = if (isRefresh) ChapterDownloadStatus.REDOWNLOADING.ordinal
                                               else ChapterDownloadStatus.DOWNLOADING.ordinal
                                chapterDao.setDownloadStatus(ch.id, inflight)
                                ch to isRefresh
                            } ?: break
                            val chapter = picked.first
                            val isRefresh = picked.second
                            var redactName = false
                            runCatching {
                                val novel = novelDao.getById(chapter.novelId) ?: error("Novel not found")
                                redactName = !authManager.isUnlocked.value &&
                                    novel.categoryId != null &&
                                    categoryDao.getAllOnce()
                                        .firstOrNull { it.id == novel.categoryId }?.isHidden == true
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
                                val errorStatus = if (isRefresh) ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal
                                                  else ChapterDownloadStatus.ERROR.ordinal
                                chapterDao.setDownloadStatus(chapter.id, errorStatus)
                            }
                            val displayName = if (redactName) "" else chapter.name
                            onProgress(displayName, chapterDao.getQueuedCount())
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
