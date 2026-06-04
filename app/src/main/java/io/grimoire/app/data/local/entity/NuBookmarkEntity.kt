package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved NovelUpdates series — a lightweight "read it later" pointer, distinct
 * from the library (which tracks a source-backed novel + its chapters). Keyed by
 * the NU [slug] so re-opening just navigates to the series page; the rest is
 * cached display metadata so the bookmarks grid renders without a network hit.
 */
@Entity(tableName = "nu_bookmarks")
data class NuBookmarkEntity(
    @PrimaryKey val slug: String,
    val url: String,
    val title: String,
    val coverUrl: String? = null,
    val addedAt: Long,
)
