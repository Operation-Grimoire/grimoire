package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.grimoire.app.data.local.entity.RepoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos ORDER BY addedAt ASC")
    fun getAllFlow(): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos WHERE enabled = 1 ORDER BY addedAt ASC")
    suspend fun getEnabled(): List<RepoEntity>

    @Query("SELECT * FROM repos ORDER BY addedAt ASC")
    suspend fun getAllOnce(): List<RepoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(repo: RepoEntity): Long

    @Update
    suspend fun update(repo: RepoEntity)

    @Delete
    suspend fun delete(repo: RepoEntity)
}
