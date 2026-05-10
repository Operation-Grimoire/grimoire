package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novels",
    indices = [Index(value = ["sourceId", "url"], unique = true)],
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
)
