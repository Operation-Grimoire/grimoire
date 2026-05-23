package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per chapter discovered by a library refresh. The novel/chapter fields are
 * denormalized snapshots so the log keeps reading correctly even after the chapter
 * list is replaced by a later refresh. Append-only: rows are never edited, only the
 * user-triggered "Clear log" action removes them.
 */
@Entity(
    tableName = "library_updates",
    foreignKeys = [ForeignKey(
        entity = NovelEntity::class,
        parentColumns = ["id"],
        childColumns = ["novelId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("novelId")],
)
data class LibraryUpdateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val sourcePackage: String,
    val novelUrl: String,
    val novelTitle: String,
    val novelThumbnailUrl: String? = null,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Float = -1f,
    val foundAt: Long,
    /** Snapshot of the chapter's lock state at discovery; shown as a lock badge in the log. */
    val locked: Boolean = false,
)
