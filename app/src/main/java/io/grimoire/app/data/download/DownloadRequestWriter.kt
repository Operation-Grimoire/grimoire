package io.grimoire.app.data.download

import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity

/** Persists accepted requests before authorizing a service drain. */
internal class DownloadRequestWriter(
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val onAccepted: suspend () -> Unit,
) {
    suspend fun editableChapters(chapters: List<ChapterEntity>): List<ChapterEntity> {
        val sources = chapters.map { it.novelId }.distinct()
            .associateWith { novelDao.getById(it)?.sourceId }
        return chaptersForDownloadChanges(chapters, sources)
    }

    suspend fun enqueue(chapters: List<ChapterEntity>, force: Boolean, startService: Boolean) {
        val eligible = editableChapters(chapters).filterNot { it.locked }
        val freshIds = eligible.filter {
            it.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
                it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal
        }.map { it.id }
        val refreshIds = if (force) eligible.filter {
            it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal ||
                it.downloadStatus == ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal
        }.map { it.id } else emptyList()
        freshIds.chunked(999).forEach {
            chapterDao.setDownloadStatusBatch(it, ChapterDownloadStatus.QUEUED.ordinal)
        }
        refreshIds.chunked(999).forEach {
            chapterDao.setDownloadStatusBatch(it, ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal)
        }
        // Re-read stored state: queued snapshots may have been cancelled meanwhile.
        if (startService && eligible.map { it.id }.chunked(999).any { chapterDao.countQueuedDownloads(it) > 0 }) {
            onAccepted()
        }
    }

    suspend fun retryChapter(chapter: ChapterEntity) {
        if (canManageDownloads(novelDao.getById(chapter.novelId)?.sourceId) &&
            chapterDao.retryDownload(chapter.id) > 0
        ) onAccepted()
    }

    suspend fun retryAll(novelId: Long) {
        if (canManageDownloads(novelDao.getById(novelId)?.sourceId) && chapterDao.retryAllFailed(novelId) > 0) {
            onAccepted()
        }
    }
}
