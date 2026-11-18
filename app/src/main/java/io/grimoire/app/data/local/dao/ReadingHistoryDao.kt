package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY openedAt DESC")
    fun getAll(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT id FROM reading_history WHERE sourcePackage=:source AND novelUrl=:novel AND chapterUrl=:chapter")
    suspend fun existingId(source: String, novel: String, chapter: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ReadingHistoryEntity)

    @Transaction
    suspend fun refresh(entry: ReadingHistoryEntity) {
        val id = existingId(entry.sourcePackage, entry.novelUrl, entry.chapterUrl) ?: 0L
        // Keep the ID carried by a History selection even if an accepted reopen runs first.
        upsert(entry.copy(id = id))
    }

    @Query("DELETE FROM reading_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Transaction
    suspend fun deleteSelected(ids: List<Long>) {
        for (group in ids.chunked(999)) deleteByIds(group)
    }

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()
}
