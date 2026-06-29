package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per chapter opened in the reader, regardless of read progress or whether the
 * novel is in the library. The novel/chapter fields are denormalized snapshots so the
 * entry keeps rendering even after the chapter list is replaced or the novel is pruned —
 * there is intentionally no foreign key. Re-opening a chapter refreshes [openedAt] via a
 * REPLACE on the unique (sourcePackage, novelUrl, chapterUrl) index.
 */
@Entity(
    tableName = "reading_history",
    indices = [Index(value = ["sourcePackage", "novelUrl", "chapterUrl"], unique = true)],
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    /** Best-effort id of the cached novel row, for routing; null when unknown. */
    val novelId: Long? = null,
    val novelUrl: String,
    val novelTitle: String,
    val novelThumbnailUrl: String? = null,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Float = -1f,
    val openedAt: Long,
)
