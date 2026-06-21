package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user bookmark placed *inside* a chapter's text (issue #132) — either a point
 * between two words or a highlighted range. Distinct from [NuBookmarkEntity]
 * (NovelUpdates series bookmarks).
 *
 * Position is character-level: [startPage]/[endPage] index the chapter's paragraph
 * list (the reader's `pages`), [startChar]/[endChar] are character offsets within
 * those paragraphs. A point bookmark has start == end ([isHighlight] = false); a
 * highlight spans start..end.
 *
 * [text] snapshots the highlighted (or surrounding) prose. It is shown in the
 * novel-detail bookmark list and used to re-find the position by text when
 * paragraph indices/offsets shift after a chapter redownload.
 *
 * [colorIndex] selects a palette colour; each bookmark in a chapter gets a distinct
 * one (see ui/screen/reader/BookmarkColors.kt). Keyed by [novelId] + [chapterUrl]
 * (not chapterId, which a redownload re-keys).
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["novelId"]), Index(value = ["novelId", "chapterUrl"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val chapterUrl: String,
    val chapterName: String,
    val startPage: Int,
    val startChar: Int,
    val endPage: Int,
    val endChar: Int,
    val isHighlight: Boolean,
    val text: String,
    val colorIndex: Int,
    val note: String? = null,
    val createdAt: Long,
)
