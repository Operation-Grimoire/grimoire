package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.grimoire.api.model.novel.NovelPage

// Keep existing Android call sites and persisted bytes stable; both hosts use one codec.
val CHAPTER_PAGE_SEPARATOR = io.grimoire.reading.CHAPTER_PAGE_SEPARATOR
val CHAPTER_IMAGE_MARKER = io.grimoire.reading.CHAPTER_IMAGE_MARKER
val CHAPTER_SEPARATOR_MARKER = io.grimoire.reading.CHAPTER_SEPARATOR_MARKER
val CHAPTER_FORMATTED_MARKER = io.grimoire.reading.CHAPTER_FORMATTED_MARKER

fun encodeChapterContent(pages: List<NovelPage>): String = io.grimoire.reading.encodeChapterContent(pages)
fun decodeChapterContent(content: String): List<NovelPage> = io.grimoire.reading.decodeChapterContent(content)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = NovelEntity::class,
        parentColumns = ["id"],
        childColumns = ["novelId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("novelId"),
        Index("downloadStatus"),
        // Covering index for the per-novel stats aggregation and the transient-novel
        // prune, so both read only this index and never the chapter rows. See MIGRATION_30_31.
        Index(value = ["novelId", "read", "downloadStatus", "locked"]),
    ],
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
    val readAnchorItemIndex: Int = 0,
    val readAnchorItemOffset: Int = 0,
    val downloadStatus: Int = 0,
    val queueOrder: Long = 0L,
    val firstReadAt: Long? = null,
    val wordCount: Int = 0,
    /** Chapter is gated behind a paid account on the source: shown disabled, not readable. */
    val locked: Boolean = false,
)
