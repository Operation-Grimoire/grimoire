package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.LibraryStats
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.ReadingStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT id, novelId, url, name, uploadDate, chapterNumber, translator, read, readProgress, downloadStatus, queueOrder, firstReadAt, wordCount FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC")
    fun getChapters(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC")
    suspend fun getChaptersOnce(novelId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters")
    suspend fun getAll(): List<ChapterEntity>

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

    @Query(
        "UPDATE chapters SET read = :read, " +
            "firstReadAt = CASE WHEN :read AND firstReadAt IS NULL " +
            "  THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE firstReadAt END " +
            "WHERE id = :id"
    )
    suspend fun setReadRaw(id: Long, read: Boolean)

    suspend fun setRead(id: Long, read: Boolean) {
        setReadRaw(id, read)
        if (read) backfillWordCountsFromDownloads()
    }

    @Query("UPDATE chapters SET readProgress = :progress WHERE id = :id")
    suspend fun setReadProgress(id: Long, progress: Float)

    @Query("UPDATE chapters SET wordCount = :count WHERE id = :id AND wordCount != :count")
    suspend fun setWordCount(id: Long, count: Int)

    @Query(
        "UPDATE chapters SET read = :read, " +
            "firstReadAt = CASE WHEN :read AND firstReadAt IS NULL " +
            "  THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE firstReadAt END " +
            "WHERE novelId = :novelId"
    )
    suspend fun markAllReadRaw(novelId: Long, read: Boolean)

    suspend fun markAllRead(novelId: Long, read: Boolean) {
        markAllReadRaw(novelId, read)
        if (read) backfillWordCountsFromDownloads()
    }

    @Query(
        "UPDATE chapters SET read = :read, " +
            "firstReadAt = CASE WHEN :read AND firstReadAt IS NULL " +
            "  THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE firstReadAt END " +
            "WHERE novelId = :novelId AND chapterNumber <= :chapterNumber"
    )
    suspend fun markAllBeforeRaw(novelId: Long, chapterNumber: Float, read: Boolean)

    suspend fun markAllBefore(novelId: Long, chapterNumber: Float, read: Boolean) {
        markAllBeforeRaw(novelId, chapterNumber, read)
        if (read) backfillWordCountsFromDownloads()
    }

    @Query(
        "UPDATE chapters SET read = :read, " +
            "firstReadAt = CASE WHEN :read AND firstReadAt IS NULL " +
            "  THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE firstReadAt END " +
            "WHERE novelId = :novelId AND chapterNumber >= :chapterNumber"
    )
    suspend fun markAllAfterRaw(novelId: Long, chapterNumber: Float, read: Boolean)

    suspend fun markAllAfter(novelId: Long, chapterNumber: Float, read: Boolean) {
        markAllAfterRaw(novelId, chapterNumber, read)
        if (read) backfillWordCountsFromDownloads()
    }

    @Query(
        "UPDATE chapters SET read = :read, " +
            "firstReadAt = CASE WHEN :read AND firstReadAt IS NULL " +
            "  THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE firstReadAt END " +
            "WHERE id IN (:ids)"
    )
    suspend fun markChaptersRaw(ids: List<Long>, read: Boolean)

    suspend fun markChapters(ids: List<Long>, read: Boolean) {
        markChaptersRaw(ids, read)
        if (read) backfillWordCountsFromDownloads()
    }

    @Query("UPDATE chapters SET downloadStatus = :status WHERE id = :id")
    suspend fun setDownloadStatus(id: Long, status: Int)

    @Query("UPDATE chapters SET downloadStatus = :status WHERE id IN (:ids)")
    suspend fun setDownloadStatusBatch(ids: List<Long>, status: Int)

    @Query(
        "UPDATE chapters SET downloadStatus = :status, downloadedContent = :content, " +
            "wordCount = CASE WHEN :wordCount > 0 THEN :wordCount ELSE wordCount END " +
            "WHERE id = :id"
    )
    suspend fun setDownloadedContentRaw(id: Long, content: String, status: Int, wordCount: Int)

    suspend fun setDownloadedContent(id: Long, content: String, status: Int) {
        setDownloadedContentRaw(id, content, status, countWordsIn(content))
    }

    @Query("UPDATE chapters SET downloadStatus = 0, downloadedContent = NULL WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("SELECT * FROM chapters WHERE downloadStatus = 1 ORDER BY queueOrder DESC, id ASC LIMIT 1")
    suspend fun getNextQueued(): ChapterEntity?

    @Query("UPDATE chapters SET queueOrder = :order WHERE novelId = :novelId AND downloadStatus = 1")
    suspend fun setQueueOrder(novelId: Long, order: Long)

    @Query("UPDATE chapters SET queueOrder = :order WHERE id = :id")
    suspend fun setChapterQueueOrder(id: Long, order: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE downloadStatus = 1")
    suspend fun getQueuedCount(): Int

    @Query("UPDATE chapters SET downloadStatus = 1 WHERE downloadStatus = 2")
    suspend fun resetStuckDownloads()

    @Query("UPDATE chapters SET downloadStatus = 0 WHERE novelId = :novelId AND downloadStatus = 1")
    suspend fun cancelAllQueued(novelId: Long)

    @Query("UPDATE chapters SET downloadStatus = 0, downloadedContent = NULL WHERE novelId = :novelId AND downloadStatus = 3")
    suspend fun deleteAllDownloads(novelId: Long)

    @Query("UPDATE chapters SET downloadStatus = 1 WHERE novelId = :novelId AND downloadStatus = 4")
    suspend fun retryAllFailed(novelId: Long)

    @Query("SELECT id, novelId, url, name, uploadDate, chapterNumber, translator, read, readProgress, downloadStatus, queueOrder, firstReadAt, wordCount FROM chapters WHERE downloadStatus != 0 ORDER BY novelId ASC, chapterNumber ASC")
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

    @Query("""
        SELECT id, downloadedContent FROM chapters
        WHERE read = 1 AND wordCount = 0 AND downloadedContent IS NOT NULL
    """)
    suspend fun getReadDownloadedMissingWordCount(): List<ChapterContent>

    suspend fun backfillWordCountsFromDownloads() {
        for (c in getReadDownloadedMissingWordCount()) {
            val n = countWordsIn(c.downloadedContent)
            if (n > 0) setWordCount(c.id, n)
        }
    }

    @Query("""
        SELECT
          (SELECT COUNT(*) FROM chapters WHERE firstReadAt IS NOT NULL) AS chaptersRead,
          (SELECT COALESCE(SUM(wordCount), 0) FROM chapters WHERE firstReadAt IS NOT NULL) AS wordsRead,
          (SELECT COUNT(DISTINCT novelId) FROM chapters WHERE firstReadAt IS NOT NULL) AS novelsStarted,
          (SELECT COUNT(*) FROM novels n
             WHERE EXISTS (SELECT 1 FROM chapters WHERE novelId = n.id)
               AND NOT EXISTS (SELECT 1 FROM chapters WHERE novelId = n.id AND firstReadAt IS NULL)
          ) AS novelsCompleted,
          (SELECT MIN(firstReadAt) FROM chapters WHERE firstReadAt IS NOT NULL) AS firstReadAt,
          (SELECT MAX(firstReadAt) FROM chapters WHERE firstReadAt IS NOT NULL) AS lastReadAt
    """)
    fun getReadingStats(): Flow<ReadingStats>

    @Query("""
        SELECT
          (SELECT COUNT(*) FROM novels WHERE favorite = 1) AS favoriteNovels,
          (SELECT COUNT(*) FROM chapters c
             JOIN novels n ON c.novelId = n.id WHERE n.favorite = 1) AS libraryChapters,
          (SELECT COUNT(*) FROM chapters c
             JOIN novels n ON c.novelId = n.id WHERE n.favorite = 1 AND c.read = 0) AS libraryUnreadChapters,
          (SELECT COUNT(*) FROM chapters WHERE downloadStatus = 3) AS downloadedChapters
    """)
    fun getLibraryStats(): Flow<LibraryStats>
}

data class ChapterContent(val id: Long, val downloadedContent: String)

private fun countWordsIn(s: String): Int {
    var count = 0
    var inWord = false
    for (ch in s) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}
