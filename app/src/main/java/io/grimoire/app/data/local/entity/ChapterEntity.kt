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

/**
 * Token value marking a persisted page as a scene-break (NovelPage.isSeparator). Uses the
 * ASCII Group Separator, distinct from the image marker so old downloads (which contain
 * neither) keep decoding as plain text.
 */
val CHAPTER_SEPARATOR_MARKER: String = 29.toChar().toString()

/**
 * Within a text page token, splits the plain `text` field from an optional `formattedText`
 * (constrained-HTML) payload. ASCII File Separator — same rationale: legacy tokens without
 * this byte decode unchanged with `formattedText = null`.
 */
val CHAPTER_FORMATTED_MARKER: String = 28.toChar().toString()

/** Serialises chapter [pages] into the single string stored in [ChapterEntity.downloadedContent]. */
fun encodeChapterContent(pages: List<NovelPage>): String =
    pages.joinToString(CHAPTER_PAGE_SEPARATOR) { page ->
        when {
            page.isSeparator -> CHAPTER_SEPARATOR_MARKER
            page.imageUrl != null -> CHAPTER_IMAGE_MARKER + page.imageUrl
            page.formattedText != null -> page.text + CHAPTER_FORMATTED_MARKER + page.formattedText
            else -> page.text
        }
    }

/** Reconstructs the chapter pages previously serialised by [encodeChapterContent]. */
fun decodeChapterContent(content: String): List<NovelPage> =
    content.split(CHAPTER_PAGE_SEPARATOR).mapIndexed { index, token ->
        when {
            token == CHAPTER_SEPARATOR_MARKER ->
                NovelPage(index = index, text = "", isSeparator = true)
            token.startsWith(CHAPTER_IMAGE_MARKER) ->
                NovelPage(index = index, text = "", imageUrl = token.removePrefix(CHAPTER_IMAGE_MARKER))
            token.contains(CHAPTER_FORMATTED_MARKER) -> {
                val parts = token.split(CHAPTER_FORMATTED_MARKER, limit = 2)
                NovelPage(index = index, text = parts[0], formattedText = parts[1])
            }
            else -> NovelPage(index = index, text = token)
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
    indices = [Index("novelId"), Index("downloadStatus")],
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
    val downloadedContent: String? = null,
    val queueOrder: Long = 0L,
    val firstReadAt: Long? = null,
    val wordCount: Int = 0,
    /** Chapter is gated behind a paid account on the source: shown disabled, not readable. */
    val locked: Boolean = false,
)
