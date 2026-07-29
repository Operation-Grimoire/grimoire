package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

data class FavoriteKey(val sourceId: Long, val url: String)

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels WHERE favorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels")
    suspend fun getAll(): List<NovelEntity>

    /**
     * Novels worth backing up: everything in the library, plus any non-favorite
     * with at least one fully-read chapter (so browse-and-bail rows stay out of
     * the backup while real read history is always preserved). A partial read
     * percentage does not count — only `read = 1`.
     */
    @Query("""
        SELECT * FROM novels
        WHERE favorite = 1
           OR id IN (SELECT DISTINCT novelId FROM chapters WHERE read = 1)
    """)
    suspend fun getForBackup(): List<NovelEntity>

    /**
     * Delete transient browse rows: not in the library, no fully-read chapter,
     * no download state, and untouched since [cutoff]. A partial read percentage
     * does not protect a row — only `read = 1`. Chapters cascade-delete.
     * Returns the number of novels removed.
     */
    @Query("""
        DELETE FROM novels
        WHERE favorite = 0
          AND lastAccessedAt < :cutoff
          AND id NOT IN (
              SELECT DISTINCT novelId FROM chapters
              WHERE read = 1 OR downloadStatus != 0
          )
    """)
    suspend fun pruneTransient(cutoff: Long): Int

    @Query("SELECT sourceId, url FROM novels WHERE favorite = 1")
    fun getFavoriteKeys(): Flow<List<FavoriteKey>>

    @Query("SELECT url FROM novels WHERE favorite = 1 AND sourceId = :sourceId")
    fun getFavoriteUrlsBySource(sourceId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM novels WHERE favorite = 1")
    suspend fun countFavorites(): Int

    /**
     * Non-favorite browse rows that a "clear browse data" would remove right now:
     * no fully-read chapter and no download (matches [pruneTransient]'s protection,
     * minus the age filter, since a manual clear ignores the grace window).
     */
    @Query("""
        SELECT COUNT(*) FROM novels
        WHERE favorite = 0
          AND id NOT IN (
              SELECT DISTINCT novelId FROM chapters
              WHERE read = 1 OR downloadStatus != 0
          )
    """)
    suspend fun countClearableBrowse(): Int

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getById(id: Long): NovelEntity?

    /**
     * The library novel the user read most recently, or null if nothing's been read yet.
     * When [excludeHidden] is set (the library is locked) novels in a hidden category are
     * skipped, so the "continue reading" shortcut never leaks a hidden novel while locked.
     */
    @Query("""
        SELECT n.* FROM novels n
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE n.favorite = 1 AND n.lastReadAt > 0
          AND (:excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0)
        ORDER BY n.lastReadAt DESC
        LIMIT 1
    """)
    suspend fun getMostRecentlyReadFavorite(excludeHidden: Boolean): NovelEntity?

    @Query("SELECT * FROM novels WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE sourceId = :sourceId AND url = :url")
    suspend fun getBySourceUrl(sourceId: Long, url: String): NovelEntity?

    @Upsert
    suspend fun upsert(novel: NovelEntity): Long

    /** Re-keys a novel's source identity. Used by the one-time source-id migration. */
    @Query("UPDATE novels SET sourceId = :sourceId WHERE id = :id")
    suspend fun updateSourceId(id: Long, sourceId: Long)

    @Query("UPDATE novels SET chapterSortOrder = :order WHERE id = :id")
    suspend fun updateChapterSort(id: Long, order: Int)

    @Query("UPDATE novels SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    @Query("UPDATE novels SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE novels SET readerTextAlign = :align WHERE id = :id")
    suspend fun updateReaderTextAlign(id: Long, align: Int)

    @Query("UPDATE novels SET lastReadAt = :timestamp WHERE id = :id")
    suspend fun updateLastReadAt(id: Long, timestamp: Long)

    /** Bump the browse access time so re-opening a cached novel extends its prune TTL. */
    @Query("UPDATE novels SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun touchAccessed(id: Long, timestamp: Long)

    @Query("UPDATE novels SET notifyOnNewChapters = :value WHERE id = :id")
    suspend fun updateNotifyOnNewChapters(id: Long, value: Boolean)

    @Query("UPDATE novels SET notifyOnNewLockedChapters = :value WHERE id = :id")
    suspend fun updateNotifyOnNewLockedChapters(id: Long, value: Boolean)

    @Query("UPDATE novels SET autoDownloadNewChapters = :value WHERE id = :id")
    suspend fun updateAutoDownloadNewChapters(id: Long, value: Boolean)

    /** Write the user's 1–10 rating. Pass null to clear it (back to unrated). */
    @Query("UPDATE novels SET userRating = :rating WHERE id = :id")
    suspend fun updateUserRating(id: Long, rating: Int?)

    /** Write the per-field metadata overrides (#152). A null clears that field's override. */
    @Query("""
        UPDATE novels SET
            overrideTitle = :title,
            overrideAuthor = :author,
            overrideDescription = :description,
            overrideStatus = :status,
            overrideGenres = :genres
        WHERE id = :id
    """)
    suspend fun updateMetadataOverrides(
        id: Long,
        title: String?,
        author: String?,
        description: String?,
        status: Int?,
        genres: String?,
    )

    /** Write the cover override (#151). Pass both null to reset to the source cover. */
    @Query("UPDATE novels SET customCoverPath = :path, customCoverUrl = :url WHERE id = :id")
    suspend fun updateCustomCover(id: Long, path: String?, url: String?)

    @Query("""
        SELECT n.id FROM novels n
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE (n.notifyOnNewChapters = 1 OR n.notifyOnNewLockedChapters = 1)
          AND (:excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0)
    """)
    fun getSubscribedNovelIds(excludeHidden: Boolean): Flow<List<Long>>

    @Delete
    suspend fun delete(novel: NovelEntity)
}
