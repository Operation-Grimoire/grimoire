package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = NovelEntity::class,
        parentColumns = ["id"],
        childColumns = ["novelId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("novelId")],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val url: String,
    val name: String,
    val uploadDate: Long = 0L,
    val chapterNumber: Float = -1f,
    val translator: String? = null,
    val read: Boolean = false,
    val readProgress: Float = 0f,
)
