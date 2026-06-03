package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.grimoire.app.data.local.entity.TaskLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskLogDao {
    @Query("SELECT * FROM task_log ORDER BY completedAt DESC, id DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<TaskLogEntity>>

    @Insert
    suspend fun insert(entry: TaskLogEntity)

    /** Drops everything but the [keep] newest rows so the log can't grow unbounded. */
    @Query(
        """
        DELETE FROM task_log WHERE id NOT IN (
            SELECT id FROM task_log ORDER BY completedAt DESC, id DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM task_log")
    suspend fun clearAll()

    /** Records a finished run and trims the log back to [MAX_ENTRIES] in one step. */
    suspend fun record(entry: TaskLogEntity) {
        insert(entry)
        trimTo(MAX_ENTRIES)
    }

    companion object {
        /** Hard cap on retained history rows; older entries fall off as new ones land. */
        const val MAX_ENTRIES = 200
    }
}
