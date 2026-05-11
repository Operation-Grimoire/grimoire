package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC")
    fun getChapters(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC")
    suspend fun getChaptersOnce(novelId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND url = :url")
    suspend fun getByUrl(novelId: Long, url: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): ChapterEntity?

    @Insert
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteByNovelId(novelId: Long)

    @Transaction
    suspend fun replaceChapters(novelId: Long, chapters: List<ChapterEntity>) {
        deleteByNovelId(novelId)
        insertAll(chapters)
    }

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET read = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE chapters SET readProgress = :progress WHERE id = :id")
    suspend fun setReadProgress(id: Long, progress: Float)

    @Query("UPDATE chapters SET read = :read WHERE novelId = :novelId")
    suspend fun markAllRead(novelId: Long, read: Boolean)

    @Query("UPDATE chapters SET read = :read WHERE novelId = :novelId AND chapterNumber <= :chapterNumber")
    suspend fun markAllBefore(novelId: Long, chapterNumber: Float, read: Boolean)

    @Query("UPDATE chapters SET read = :read WHERE novelId = :novelId AND chapterNumber >= :chapterNumber")
    suspend fun markAllAfter(novelId: Long, chapterNumber: Float, read: Boolean)

    @Query("UPDATE chapters SET downloadStatus = :status WHERE id = :id")
    suspend fun setDownloadStatus(id: Long, status: Int)

    @Query("UPDATE chapters SET downloadStatus = :status, downloadedContent = :content WHERE id = :id")
    suspend fun setDownloadedContent(id: Long, content: String, status: Int)

    @Query("UPDATE chapters SET downloadStatus = 0, downloadedContent = NULL WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("SELECT * FROM chapters WHERE downloadStatus = 1 ORDER BY queueOrder DESC, id ASC LIMIT 1")
    suspend fun getNextQueued(): ChapterEntity?

    @Query("UPDATE chapters SET queueOrder = :order WHERE novelId = :novelId AND downloadStatus = 1")
    suspend fun setQueueOrder(novelId: Long, order: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE downloadStatus = 1")
    suspend fun getQueuedCount(): Int

    @Query("UPDATE chapters SET downloadStatus = 1 WHERE downloadStatus = 2")
    suspend fun resetStuckDownloads()

    @Query("UPDATE chapters SET downloadStatus = 0 WHERE novelId = :novelId AND downloadStatus = 1")
    suspend fun cancelAllQueued(novelId: Long)

    @Query("UPDATE chapters SET downloadStatus = 0, downloadedContent = NULL WHERE novelId = :novelId AND downloadStatus = 3")
    suspend fun deleteAllDownloads(novelId: Long)

    @Query("SELECT * FROM chapters WHERE downloadStatus != 0 ORDER BY novelId ASC, chapterNumber ASC")
    fun getAllDownloads(): Flow<List<ChapterEntity>>

    @Query("""
        SELECT novelId,
               COUNT(*) AS total,
               SUM(read) AS readCount,
               SUM(CASE WHEN downloadStatus = 3 THEN 1 ELSE 0 END) AS downloadedCount
        FROM chapters
        GROUP BY novelId
    """)
    fun getStatsForAll(): Flow<List<NovelChapterStats>>
}
