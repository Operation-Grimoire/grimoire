package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Protocol metadata is separate from chapter/progress projections and cascades with its incarnation. */
@Entity(tableName = "chapter_download_state", foreignKeys = [ForeignKey(
    entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index(value = ["activeAttemptId"], unique = true), Index(value = ["committedAttemptId"], unique = true)])
data class ChapterDownloadStateEntity(
    @PrimaryKey val chapterId: Long,
    val revision: String,
    val activeAttemptId: String? = null,
    val activeOwner: String? = null,
    val baseRevision: String? = null,
    val committedAttemptId: String? = null,
    val digest: String? = null,
    val contentVersion: Int? = null,
    val wordCount: Int? = null,
    val cacheRevision: String? = null,
)
