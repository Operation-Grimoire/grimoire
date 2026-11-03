package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A downloaded chapter's saved text ([encodeChapterContent] format), one row per
 * chapter that has content.
 *
 * Kept out of `chapters` on purpose: each body is tens of KB, and inline it made
 * every downloaded chapter row roughly a page wide, so any query that walked chapter
 * rows (library stats, prune, chapter lists) paged in every downloaded body just to
 * read a few integer flags. Row ids key it, so content follows its chapter through
 * in-place reconciles and is cascade-deleted with it. See `ChapterContentMigration`.
 */
@Entity(
    tableName = "chapter_content",
    foreignKeys = [ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["id"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChapterContentEntity(
    @PrimaryKey val chapterId: Long,
    val content: String,
)
