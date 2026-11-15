package io.grimoire.app.data.download

import androidx.room.withTransaction
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.RoomDownloadDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.storage.DownloadAttempt
import io.grimoire.reading.storage.DownloadRepository
import javax.inject.Inject

/** Android scheduling chooses a row; the shared facade claims its portable identity. */
class AndroidDownloadQueue @Inject constructor(
    private val database: AppDatabase,
    private val downloads: DownloadRepository,
    private val adapter: RoomDownloadDao,
) {
    suspend fun resetInterrupted() = adapter.resetInterruptedDownloads()

    suspend fun take(): ClaimedAndroidDownload? = database.withTransaction {
        val chapter = database.chapterDao().getNextQueued() ?: return@withTransaction null
        val novel = database.novelDao().getById(chapter.novelId) ?: return@withTransaction null
        val key = ChapterKey(NovelKey(novel.sourceId, novel.url), chapter.url)
        val attempt = downloads.claim(key) ?: return@withTransaction null
        ClaimedAndroidDownload(chapter, attempt)
    }
}

data class ClaimedAndroidDownload(val chapter: ChapterEntity, val attempt: DownloadAttempt)
