package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.NuBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NuBookmarkDao {
    @Query("SELECT * FROM nu_bookmarks ORDER BY addedAt DESC")
    fun getAll(): Flow<List<NuBookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM nu_bookmarks WHERE slug = :slug)")
    fun isBookmarked(slug: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(bookmark: NuBookmarkEntity)

    @Query("DELETE FROM nu_bookmarks WHERE slug = :slug")
    suspend fun delete(slug: String)
}
