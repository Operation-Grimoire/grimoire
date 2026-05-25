package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.grimoire.app.data.local.entity.UpdateIssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateIssueDao {
    @Query("""
        SELECT ui.* FROM update_issues ui
        INNER JOIN novels n ON n.id = ui.novelId
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE :excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0
        ORDER BY ui.severity DESC, ui.occurredAt DESC
    """)
    fun getAll(excludeHidden: Boolean): Flow<List<UpdateIssueEntity>>

    @Query("""
        SELECT COUNT(*) FROM update_issues ui
        INNER JOIN novels n ON n.id = ui.novelId
        LEFT JOIN categories c ON c.id = n.categoryId
        WHERE :excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0
    """)
    fun count(excludeHidden: Boolean): Flow<Int>

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
