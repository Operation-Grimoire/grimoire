package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.grimoire.app.data.local.entity.UpdateIssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateIssueDao {
    @Query("SELECT * FROM update_issues ORDER BY severity DESC, occurredAt DESC")
    fun getAll(): Flow<List<UpdateIssueEntity>>

    @Query("SELECT COUNT(*) FROM update_issues")
    fun count(): Flow<Int>

    @Insert
    suspend fun insert(issue: UpdateIssueEntity)

    @Query("DELETE FROM update_issues WHERE novelId = :novelId")
    suspend fun clearForNovel(novelId: Long)

    /** Replaces any existing issue for the novel, keeping exactly one row per novel. */
    @Transaction
    suspend fun setIssue(issue: UpdateIssueEntity) {
        clearForNovel(issue.novelId)
        insert(issue)
    }
}
