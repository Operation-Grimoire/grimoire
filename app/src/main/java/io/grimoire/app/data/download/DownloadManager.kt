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
import kotlinx.coroutines.flow.first
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
        requestServiceStart()
    }

    fun setConcurrency(value: Int) {
        scope.launch { downloadPreferences.concurrency.set(value.coerceIn(1, 5)) }
    }

    /**
     * Queues [chapters] for download. [startService] wakes [DownloadService] so
     * the queue drains on its own with its own notification — the right thing
     * for a user-initiated download. The library sync passes `false`: it drains
     * the queue inline from its foreground worker, so a second download
     * notification would just collide with the sync's own progress notification.
     */
    fun enqueue(chapters: List<ChapterEntity>, force: Boolean = false, startService: Boolean = true) {
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
        if (startService) requestServiceStart()
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
        requestServiceStart()
    }

    fun retryAll(novelId: Long) {
        scope.launch { chapterDao.retryAllFailed(novelId) }
        _isPaused.value = false
        requestServiceStart()
    }

    /**
     * Tries to wake [DownloadService]. Android 12+ refuses a foreground service
     * start from a background-only context (e.g. a WorkManager worker), so we
     * swallow the [IllegalStateException] subclass; the queued rows stay in the
     * DB and the calling worker (or the next foreground tap) drains them.
     */
    private fun requestServiceStart() {
        try {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException on API 31+.
        }
    }

    fun cancelAllFailed(novelId: Long) {
        scope.launch { chapterDao.cancelAllFailed(novelId) }
    }

    suspend fun processQueue(onProgress: (chapterName: String, remaining: Int) -> Unit): DownloadBatchResult {
        if (!isProcessing.compareAndSet(false, true)) return DownloadBatchResult.SKIPPED
        // Wait for the extension scan: a drain kicked off right after process start
        // (e.g. WorkManager re-running a queued batch) would otherwise see an empty
        // extensions list and fail every chapter with "Source not available".
        extensionManager.awaitReady()
        // Read the preference directly rather than the eagerly-shared StateFlow,
        // whose first value is the default until the DataStore read lands.
        val n = downloadPreferences.concurrency.changes().first().coerceIn(1, 5)
        val downloaded = AtomicInteger(0)
        val failed = AtomicInteger(0)
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
                            // Look up the novel before starting the fetch so the progress
                            // notification can announce what's currently being downloaded —
                            // not just what just finished. With concurrent downloads the
                            // post-completion callback alone made the notification look
                            // frozen on whichever chapter happened to finish last.
                            val novel = novelDao.getById(chapter.novelId)
                            val redactName = novel != null &&
                                !authManager.isUnlocked.value &&
                                novel.categoryId != null &&
                                categoryDao.getAllOnce()
                                    .firstOrNull { it.id == novel.categoryId }?.isHidden == true
                            val displayName = if (redactName) "" else chapter.name
                            onProgress(displayName, chapterDao.getQueuedCount())

                            runCatching {
                                if (novel == null) error("Novel not found")
                                val src = extensionManager.extensions.value
                                    .firstOrNull { it.id == novel.sourceId }?.source
                                    ?: error("Source not available")
                                // Retry transient empty responses (the Royal Road blank-chapter
                                // bug) before giving up; throws EmptyChapterContentException when
                                // every attempt comes back empty, so the chapter fails instead of
                                // persisting a blank.
                                val pages = fetchReadablePages { src.getPageList(chapter.toChapter()) }
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
                                failed.incrementAndGet()
                            }
                        }
                    }
                }
            }
        } finally {
            isProcessing.set(false)
        }
        return DownloadBatchResult(downloaded = downloaded.get(), failed = failed.get())
    }
}

/**
 * Outcome of one [DownloadManager.processQueue] drain. [skipped] is true when
 * another drain was already in flight (the call did nothing and should not be
 * treated as a finished batch).
 */
data class DownloadBatchResult(
    val downloaded: Int,
    val failed: Int,
    val skipped: Boolean = false,
) {
    companion object {
        val SKIPPED = DownloadBatchResult(downloaded = 0, failed = 0, skipped = true)
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
