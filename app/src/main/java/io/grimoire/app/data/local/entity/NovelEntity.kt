package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novels",
    indices = [
        Index(value = ["sourceId", "url"], unique = true),
        // The library list and every favorites-scoped query filter on `favorite = 1`;
        // without this the whole novels table is scanned on each app open / refresh.
        Index(value = ["favorite"]),
    ],
)
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: String = "",
    val status: Int = 0,
    val favorite: Boolean = false,
    val lastUpdated: Long = 0L,
    val chapterSortOrder: Int = 0,
    val categoryId: Long? = null,
    val lastReadAt: Long = 0L,
    /**
     * Wall-clock millis the novel was last opened from browse. Used to age-out
     * transient rows: a non-favorite with no fully-read chapter is pruned once
     * this passes [io.grimoire.app.data.local.TransientNovelPruner.STALE_AFTER_MS].
     */
    val lastAccessedAt: Long = 0L,
    val rating: Float? = null,
    val ratingCount: Int? = null,
    val language: String? = null,
    val notifyOnNewChapters: Boolean = false,
    val notifyOnNewLockedChapters: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
)
