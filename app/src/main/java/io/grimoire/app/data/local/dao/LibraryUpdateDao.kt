package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryUpdateDao {
    @Query("SELECT * FROM library_updates ORDER BY foundAt DESC, id DESC")
    fun getAll(): Flow<List<LibraryUpdateEntity>>

    @Insert
    suspend fun insertAll(entries: List<LibraryUpdateEntity>)

    @Query("DELETE FROM library_updates")
    suspend fun clearAll()
}
