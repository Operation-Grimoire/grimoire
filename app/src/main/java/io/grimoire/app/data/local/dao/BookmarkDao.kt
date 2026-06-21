package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.grimoire.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    /** All bookmarks for a novel, oldest first (for the novel-detail list). */
    @Query("SELECT * FROM bookmarks WHERE novelId = :novelId ORDER BY createdAt ASC")
    fun getForNovel(novelId: Long): Flow<List<BookmarkEntity>>

    /** Bookmarks within one chapter, oldest first (drives in-text rendering + colour assignment). */
    @Query("SELECT * FROM bookmarks WHERE novelId = :novelId AND chapterUrl = :chapterUrl ORDER BY createdAt ASC")
    fun getForChapter(novelId: Long, chapterUrl: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    /** Move/resize a bookmark after a handle drag. */
    @Query("""
        UPDATE bookmarks SET
            startPage = :startPage, startChar = :startChar,
            endPage = :endPage, endChar = :endChar, text = :text
        WHERE id = :id
    """)
    suspend fun updatePosition(
        id: Long,
        startPage: Int,
        startChar: Int,
        endPage: Int,
        endChar: Int,
        text: String,
    )

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}
