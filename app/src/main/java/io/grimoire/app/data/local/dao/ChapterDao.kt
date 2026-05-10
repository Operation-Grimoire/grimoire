package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.ChapterEntity
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
}
