package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.ReadingCheckpointEntity

@Dao
interface ReadingCheckpointStateDao {
    @Query("SELECT * FROM reading_checkpoints WHERE chapterId = :id")
    suspend fun find(id: Long): ReadingCheckpointEntity?
    @Upsert suspend fun put(state: ReadingCheckpointEntity)
}
