package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY openedAt DESC")
    fun getAll(): Flow<List<ReadingHistoryEntity>>

    // REPLACE on the unique (sourcePackage, novelUrl, chapterUrl) index so re-opening a
    // chapter refreshes openedAt + the denormalized snapshot rather than piling up rows.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()
}
