package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

@Entity(
    tableName = "novels",
    indices = [
        Index(value = ["sourceId", "url"], unique = true),
        // The library list and every favorites-scoped query filter on `favorite = 1`;
        // without this the whole novels table is scanned on each app open / refresh.
        Index(value = ["favorite"]),
    ],
)
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: String = "",
    val status: Int = 0,
    val favorite: Boolean = false,
    val lastUpdated: Long = 0L,
    val chapterSortOrder: Int = 0,
    val categoryId: Long? = null,
    val lastReadAt: Long = 0L,
    /**
     * Wall-clock millis the novel was last opened from browse. Used to age-out
     * transient rows: a non-favorite with no fully-read chapter is pruned once
     * this passes [io.grimoire.app.data.local.TransientNovelPruner.STALE_AFTER_MS].
     */
    val lastAccessedAt: Long = 0L,
    val rating: Float? = null,
    val ratingCount: Int? = null,
    /** The user's own 1–10 rating for this novel; null when unrated. Distinct from the
     *  source [rating], which reflects readers on the source site. */
    val userRating: Int? = null,
    val language: String? = null,
    val notifyOnNewChapters: Boolean = false,
    val notifyOnNewLockedChapters: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
    /**
     * User cover override. Precedence when rendering: [customCoverPath] (a local
     * file in filesDir/covers/{id}) > [customCoverUrl] > [thumbnailUrl] (source).
     * Both survive a source refresh — see LibraryUpdater.mergeNovel / Novel.toEntity.
     */
    val customCoverPath: String? = null,
    val customCoverUrl: String? = null,
    /**
     * Per-field metadata overrides. A non-null value wins over the source-provided
     * column and is never touched by a refresh. Effective value = override ?: source.
     * [overrideStatus] is a [io.grimoire.api.model.novel.NovelStatus] ordinal; [overrideGenres]
     * is comma-joined ("" = override to an empty genre list, null = no override).
     */
    val overrideTitle: String? = null,
    val overrideAuthor: String? = null,
    val overrideDescription: String? = null,
    val overrideStatus: Int? = null,
    val overrideGenres: String? = null,
) {
    /** Title shown in the UI: the user override if set, otherwise the source title. */
    val effectiveTitle: String get() = overrideTitle ?: title

    /** Author shown in the UI: override if set, otherwise the source author. */
    val effectiveAuthor: String? get() = overrideAuthor ?: author

    /** [io.grimoire.api.model.novel.NovelStatus] ordinal: override if set, otherwise source. */
    val effectiveStatus: Int get() = overrideStatus ?: status

    /**
     * Cover to render: a local custom file > a custom url > the source thumbnail.
     * A [File] is returned for the local path so Coil loads it directly.
     */
    fun effectiveCoverModel(): Any? =
        customCoverPath?.let { File(it) } ?: customCoverUrl ?: thumbnailUrl
}
