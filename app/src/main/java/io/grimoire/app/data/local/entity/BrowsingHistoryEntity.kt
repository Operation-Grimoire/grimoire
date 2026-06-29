package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per novel opened in Browse that is **not** in the library. The fields are
 * denormalized snapshots (no foreign key) so the entry survives novel pruning and works
 * for sources that were never persisted as favorites. Re-opening a novel refreshes
 * [openedAt] via a REPLACE on the unique (sourcePackage, novelUrl) index; adding the novel
 * to the library drops its row (see BrowsingHistoryDao.deleteByNovel).
 */
@Entity(
    tableName = "browsing_history",
    indices = [Index(value = ["sourcePackage", "novelUrl"], unique = true)],
)
data class BrowsingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    /** Best-effort id of the cached novel row, for routing; null when unknown. */
    val novelId: Long? = null,
    val novelUrl: String,
    val novelTitle: String,
    val novelThumbnailUrl: String? = null,
    val openedAt: Long,
)
