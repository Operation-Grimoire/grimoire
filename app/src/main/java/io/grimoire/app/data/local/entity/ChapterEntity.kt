package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.grimoire.api.model.NovelPage

/**
 * Boundary written between pages when [ChapterEntity.downloadedContent] is persisted, so the
 * original paragraph structure can be reconstructed on read. Uses the ASCII Unit Separator,
 * which never occurs in prose and is treated as whitespace by trim/blank/word-count logic.
 */
val CHAPTER_PAGE_SEPARATOR: String = 31.toChar().toString()

/**
 * Prefix marking a persisted page as an illustration: the rest of the token is the image URL
 * rather than prose. Uses the ASCII Record Separator, which never occurs in real text, so
 * legacy text-only downloads (which contain no such prefix) keep decoding unchanged.
 */
val CHAPTER_IMAGE_MARKER: String = 30.toChar().toString()

/** Serialises chapter [pages] into the single string stored in [ChapterEntity.downloadedContent]. */
fun encodeChapterContent(pages: List<NovelPage>): String =
    pages.joinToString(CHAPTER_PAGE_SEPARATOR) { page ->
        page.imageUrl?.let { CHAPTER_IMAGE_MARKER + it } ?: page.text
    }

/** Reconstructs the chapter pages previously serialised by [encodeChapterContent]. */
fun decodeChapterContent(content: String): List<NovelPage> =
    content.split(CHAPTER_PAGE_SEPARATOR).mapIndexed { index, token ->
        if (token.startsWith(CHAPTER_IMAGE_MARKER)) {
            NovelPage(index = index, text = "", imageUrl = token.removePrefix(CHAPTER_IMAGE_MARKER))
        } else {
            NovelPage(index = index, text = token)
        }
    }

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
    val downloadStatus: Int = 0,
    val downloadedContent: String? = null,
    val queueOrder: Long = 0L,
    val firstReadAt: Long? = null,
    val wordCount: Int = 0,
    /** Chapter is gated behind a paid account on the source: shown disabled, not readable. */
    val locked: Boolean = false,
)
