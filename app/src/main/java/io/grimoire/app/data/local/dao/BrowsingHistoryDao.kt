package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.grimoire.app.data.local.entity.BrowsingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowsingHistoryDao {
    @Query("SELECT * FROM browsing_history ORDER BY openedAt DESC")
    fun getAll(): Flow<List<BrowsingHistoryEntity>>

    // REPLACE on the unique (sourcePackage, novelUrl) index so re-opening a novel refreshes
    // openedAt + the snapshot rather than duplicating the row.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BrowsingHistoryEntity)

    @Query("DELETE FROM browsing_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM browsing_history")
    suspend fun clearAll()

    /** Drops a novel's row when it is promoted into the library. */
    @Query("DELETE FROM browsing_history WHERE sourcePackage = :sourcePackage AND novelUrl = :novelUrl")
    suspend fun deleteByNovel(sourcePackage: String, novelUrl: String)
}
