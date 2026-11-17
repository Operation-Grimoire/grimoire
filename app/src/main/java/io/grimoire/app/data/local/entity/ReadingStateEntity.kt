package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "reading_states", primaryKeys = ["chapterId"], foreignKeys = [ForeignKey(
    entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE,
)])
data class ReadingStateEntity(val chapterId: Long, val state: String)

@Entity(tableName = "reading_state_receipts", primaryKeys = ["chapterId", "commandId"], foreignKeys = [ForeignKey(
    entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE,
)])
data class ReadingStateReceiptEntity(val chapterId: Long, val commandId: String, val receipt: String)
