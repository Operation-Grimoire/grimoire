package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryUpdateDao {
    // The LEFT JOIN keeps rows whose novel is in the default category (categoryId = NULL)
    // visible regardless of :excludeHidden, matching the LibraryScreen filter semantics.
    @Query("""
        SELECT lu.* FROM library_updates lu
        INNER JOIN novels n ON n.id = lu.novelId
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE :excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0
        ORDER BY lu.foundAt DESC, lu.id DESC
    """)
    fun getAll(excludeHidden: Boolean): Flow<List<LibraryUpdateEntity>>

    @Query("""
        SELECT COUNT(*) FROM library_updates lu
        INNER JOIN novels n ON n.id = lu.novelId
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE :excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0
    """)
    fun count(excludeHidden: Boolean): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM library_updates lu
        INNER JOIN novels n ON n.id = lu.novelId
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE (:excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0)
          AND (n.notifyOnNewChapters = 1 OR n.notifyOnNewLockedChapters = 1)
    """)
    fun countSubscribed(excludeHidden: Boolean): Flow<Int>

    @Insert
    suspend fun insertAll(entries: List<LibraryUpdateEntity>)

    @Query("DELETE FROM library_updates")
    suspend fun clearAll()

    @Query("DELETE FROM library_updates WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
