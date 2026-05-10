package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels WHERE favorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getById(id: Long): NovelEntity?

    @Query("SELECT * FROM novels WHERE sourceId = :sourceId AND url = :url")
    suspend fun getBySourceUrl(sourceId: Long, url: String): NovelEntity?

    @Upsert
    suspend fun upsert(novel: NovelEntity): Long

    @Delete
    suspend fun delete(novel: NovelEntity)
}
