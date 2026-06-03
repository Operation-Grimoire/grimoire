package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The kind of background run a [TaskLogEntity] row records. */
enum class TaskLogType { LIBRARY_SYNC, DOWNLOAD }

/**
 * One row per completed background run shown in the Tasks screen history. Only
 * aggregate, already-rendered results are stored ("Checked 12 novels · 5 new
 * chapters", "8 chapters downloaded") — never a novel/chapter title — so the log
 * carries nothing that hidden-category locking would need to redact. Append-only:
 * rows are added on completion and removed only by the user's "Clear log" action
 * or the rolling trim that caps the table at [TaskLogDao.MAX_ENTRIES].
 */
@Entity(tableName = "task_log")
data class TaskLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [TaskLogType] ordinal — which kind of run this records. */
    val type: Int,
    /** When the run finished, epoch millis. The log is ordered newest first by this. */
    val completedAt: Long,
    /** False when the run failed outright (the sync threw, a download errored). */
    val success: Boolean,
    /** One-line human-readable result shown in the log. */
    val summary: String,
)
