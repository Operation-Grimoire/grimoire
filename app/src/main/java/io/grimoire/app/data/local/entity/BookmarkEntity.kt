package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user bookmark at a specific position *within* a chapter (issue #132) — distinct
 * from [NuBookmarkEntity], which bookmarks NovelUpdates series.
 *
 * Position reuses the reader's scroll anchor ([ChapterEntity.readAnchorItemIndex] /
 * [readAnchorItemOffset]): [anchorIndex] is the LazyColumn item index (0 = the
 * chapter title, content paragraphs follow) and [anchorOffset] the pixel offset.
 *
 * [anchorTextBefore] / [anchorTextAfter] snapshot a little prose on each side of the
 * anchor. They are never shown; they let a jump re-find the position by text when the
 * paragraph indices have shifted (e.g. after a chapter redownload), so a bookmark
 * survives reflow instead of landing on a stale index.
 *
 * Keyed by [novelId] + [chapterUrl] (not chapterId): `replaceChapters` re-inserts
 * chapters with fresh ids on every refresh/redownload, so a chapterId reference would
 * dangle — the url is stable.
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
    val anchorIndex: Int,
    val anchorOffset: Int,
    val progress: Float,
    val anchorTextBefore: String,
    val anchorTextAfter: String,
    val note: String? = null,
    val createdAt: Long,
)
