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
    @Query("SELECT id, novelId, url, name, uploadDate, chapterNumber, translator, read, readProgress, readAnchorItemIndex, readAnchorItemOffset, downloadStatus, queueOrder, firstReadAt, wordCount, locked FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC")
    fun getChapters(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC")
    suspend fun getChaptersOnce(novelId: Long): List<ChapterEntity>

    /**
     * Chapters that carry restorable user state — read flag, partial progress, or
     * a first-read timestamp. Untouched chapters are pure scraped metadata that the
     * source re-supplies on next open, so they're left out of backups.
     */
    @Query("""
        SELECT * FROM chapters
        WHERE novelId = :novelId AND (read = 1 OR readProgress > 0 OR firstReadAt IS NOT NULL)
        ORDER BY chapterNumber ASC
    """)
    suspend fun getBackupChaptersOnce(novelId: Long): List<ChapterEntity>

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

    @Query("UPDATE chapters SET readProgress = :progress, readAnchorItemIndex = :index, readAnchorItemOffset = :offset WHERE id = :id")
    suspend fun setReadAnchor(id: Long, progress: Float, index: Int, offset: Int)

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

    // Picks up either a fresh QUEUED (1) or a REDOWNLOAD_QUEUED (5) — the worker reads
    // chapter.downloadStatus to decide which DOWNLOADING / ERROR variant to write back.
    @Query("SELECT * FROM chapters WHERE downloadStatus IN (1, 5) ORDER BY queueOrder DESC, id ASC LIMIT 1")
    suspend fun getNextQueued(): ChapterEntity?

    @Query("UPDATE chapters SET queueOrder = :order WHERE novelId = :novelId AND downloadStatus IN (1, 5)")
    suspend fun setQueueOrder(novelId: Long, order: Long)

    @Query("UPDATE chapters SET queueOrder = :order WHERE id = :id")
    suspend fun setChapterQueueOrder(id: Long, order: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE downloadStatus IN (1, 5)")
    suspend fun getQueuedCount(): Int

    // Reset both DOWNLOADING (2) → QUEUED (1) and REDOWNLOADING (6) → REDOWNLOAD_QUEUED (5).
    @Query(
        "UPDATE chapters " +
            "SET downloadStatus = CASE WHEN downloadStatus = 6 THEN 5 ELSE 1 END " +
            "WHERE downloadStatus IN (2, 6)"
    )
    suspend fun resetStuckDownloads()

    // Cancelling a queued row: fresh (1) → NONE (0); refresh (5) → DOWNLOADED (3), preserving
    // the existing saved content so the row keeps showing as downloaded.
    @Query(
        "UPDATE chapters " +
            "SET downloadStatus = CASE WHEN downloadStatus = 5 THEN 3 ELSE 0 END " +
            "WHERE novelId = :novelId AND downloadStatus IN (1, 5)"
    )
    suspend fun cancelAllQueued(novelId: Long)

    @Query(
        "UPDATE chapters " +
            "SET downloadStatus = CASE WHEN downloadStatus = 5 THEN 3 ELSE 0 END " +
            "WHERE downloadStatus IN (1, 5)"
    )
    suspend fun cancelAllQueuedDownloads()

    // Only deletes settled DOWNLOADED rows — leaves anything in flight to settle first.
    @Query("UPDATE chapters SET downloadStatus = 0, downloadedContent = NULL WHERE novelId = :novelId AND downloadStatus = 3")
    suspend fun deleteAllDownloads(novelId: Long)

    // Retry of failed: fresh (4) → QUEUED (1); refresh (7) → REDOWNLOAD_QUEUED (5).
    @Query(
        "UPDATE chapters " +
            "SET downloadStatus = CASE WHEN downloadStatus = 7 THEN 5 ELSE 1 END " +
            "WHERE novelId = :novelId AND downloadStatus IN (4, 7)"
    )
    suspend fun retryAllFailed(novelId: Long)

    // Cancel of failed: fresh (4) → NONE (0); refresh (7) → DOWNLOADED (3), keeping the
    // existing saved content (the failed refresh didn't wipe it).
    @Query(
        "UPDATE chapters " +
            "SET downloadStatus = CASE WHEN downloadStatus = 7 THEN 3 ELSE 0 END " +
            "WHERE novelId = :novelId AND downloadStatus IN (4, 7)"
    )
    suspend fun cancelAllFailed(novelId: Long)

    @Query("""
        SELECT ch.id, ch.novelId, ch.url, ch.name, ch.uploadDate, ch.chapterNumber,
               ch.translator, ch.read, ch.readProgress, ch.readAnchorItemIndex,
               ch.readAnchorItemOffset, ch.downloadStatus, ch.queueOrder,
               ch.firstReadAt, ch.wordCount, ch.locked
        FROM chapters ch
        INNER JOIN novels n ON n.id = ch.novelId
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE ch.downloadStatus != 0
          AND (:excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0)
        ORDER BY ch.novelId ASC, ch.chapterNumber ASC
    """)
    fun getAllDownloads(excludeHidden: Boolean): Flow<List<ChapterEntity>>

    @Query("SELECT id, novelId, url, name, uploadDate, chapterNumber, translator, read, readProgress, readAnchorItemIndex, readAnchorItemOffset, downloadStatus, queueOrder, firstReadAt, wordCount, locked FROM chapters WHERE novelId IN (:novelIds)")
    fun getChaptersForNovels(novelIds: List<Long>): Flow<List<ChapterEntity>>

    @Query("""
        SELECT novelId,
               COUNT(*) AS total,
               SUM(read) AS readCount,
               SUM(CASE WHEN downloadStatus IN (3, 5, 6, 7) THEN 1 ELSE 0 END) AS downloadedCount,
               SUM(CASE WHEN locked = 1 THEN 1 ELSE 0 END) AS lockedCount
        FROM chapters
        GROUP BY novelId
    """)
    fun getStatsForAll(): Flow<List<NovelChapterStats>>

    /**
     * Per-novel chapter stats restricted to favorited (library) novels. The library
     * only ever shows favorites, so joining on `novels.favorite = 1` keeps the GROUP BY
     * from aggregating every chapter the user has ever browsed — its cost scales with
     * the library, not the whole chapters table. The reader keeps [getStatsForAll]
     * because it needs progress for non-favorite novels opened from browse too.
     */
    @Query("""
        SELECT c.novelId AS novelId,
               COUNT(*) AS total,
               SUM(c.read) AS readCount,
               SUM(CASE WHEN c.downloadStatus IN (3, 5, 6, 7) THEN 1 ELSE 0 END) AS downloadedCount,
               SUM(CASE WHEN c.locked = 1 THEN 1 ELSE 0 END) AS lockedCount
        FROM chapters c
        INNER JOIN novels n ON n.id = c.novelId
        WHERE n.favorite = 1
        GROUP BY c.novelId
    """)
    fun getFavoriteStats(): Flow<List<NovelChapterStats>>

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
          (SELECT COUNT(*) FROM chapters WHERE downloadStatus IN (3, 5, 6, 7)) AS downloadedChapters
    """)
    fun getLibraryStats(): Flow<LibraryStats>

    /**
     * One-shot chapter counts/bytes for the Data management screen. Downloaded text
     * is sized by SUM(LENGTH(downloadedContent)) — char length, a close estimate of
     * the on-disk byte cost for mostly-Latin prose.
     */
    @Query("""
        SELECT
          (SELECT COUNT(*) FROM chapters c
             JOIN novels n ON n.id = c.novelId WHERE n.favorite = 1) AS libraryChapters,
          (SELECT COUNT(*) FROM chapters WHERE downloadedContent IS NOT NULL) AS downloadedTextCount,
          (SELECT COALESCE(SUM(LENGTH(downloadedContent)), 0) FROM chapters WHERE downloadedContent IS NOT NULL) AS downloadedTextBytes
    """)
    suspend fun getStorageChapterStats(): StorageChapterStats
}

data class StorageChapterStats(
    val libraryChapters: Int,
    val downloadedTextCount: Int,
    val downloadedTextBytes: Long,
)

data class ChapterContent(val id: Long, val downloadedContent: String)

private fun countWordsIn(s: String): Int {
    var count = 0
    var inWord = false
    // When a page carries a formattedText payload (rich-HTML duplicate of the
    // plain text, delimited by CHAPTER_FORMATTED_MARKER … CHAPTER_PAGE_SEPARATOR),
    // skip those characters — counting them would inflate the per-chapter total
    // by the same prose appearing twice plus all the HTML tags.
    val FS = 28.toChar()
    val US = 31.toChar()
    var skipping = false
    for (ch in s) {
        when {
            ch == FS -> { skipping = true; inWord = false }
            ch == US -> { skipping = false; inWord = false }
            skipping -> Unit
            ch.isWhitespace() -> inWord = false
            !inWord -> { inWord = true; count++ }
        }
    }
    return count
}
