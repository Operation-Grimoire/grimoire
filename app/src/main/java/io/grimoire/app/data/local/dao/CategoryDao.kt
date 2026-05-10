package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Delete
    suspend fun delete(category: CategoryEntity)
}
