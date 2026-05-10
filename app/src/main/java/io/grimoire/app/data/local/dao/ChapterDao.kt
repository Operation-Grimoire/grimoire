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
}
