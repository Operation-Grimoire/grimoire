package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** New protocol state never replaces chapter progress/history or saved content. */
@Entity(tableName = "reading_checkpoints", foreignKeys = [ForeignKey(
    entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE,
)])
data class ReadingCheckpointEntity(@PrimaryKey val chapterId: Long, val state: String)
