package io.grimoire.app.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long,
    val appVersionCode: Int = 0,
    val appVersionName: String = "",
    val novels: List<BackupNovel> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val repos: List<BackupRepo> = emptyList(),
    val preferences: List<BackupPreference> = emptyList(),
    val nuBookmarks: List<BackupNuBookmark> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

/** A saved NovelUpdates series (the "Saved" list, distinct from the library). */
@Serializable
data class BackupNuBookmark(
    val slug: String,
    val url: String,
    val title: String,
    val coverUrl: String? = null,
    val addedAt: Long,
)

/**
 * A single DataStore preference entry, dumped generically so every current and
 * future preference is captured without per-field backup code. [type] is a short
 * tag for the DataStore value type: b=Boolean, i=Int, l=Long, f=Float, d=Double,
 * s=String, ss=Set<String>. Primitives store their value in [value]; string sets
 * use [stringSet].
 */
@Serializable
data class BackupPreference(
    val key: String,
    val type: String,
    val value: String? = null,
    val stringSet: List<String>? = null,
)

@Serializable
data class BackupCategory(
    val name: String,
    val order: Int,
    val isDefault: Boolean,
    val isHidden: Boolean,
)

@Serializable
data class BackupRepo(
    val name: String,
    val indexUrl: String,
    val enabled: Boolean,
    val addedAt: Long,
)

@Serializable
data class BackupNovel(
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
    val categoryName: String? = null,
    val lastReadAt: Long = 0L,
    val rating: Float? = null,
    val ratingCount: Int? = null,
    val userRating: Int? = null,
    val notifyOnNewChapters: Boolean = false,
    val notifyOnNewLockedChapters: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
    val language: String? = null,
    /** [io.grimoire.app.data.local.entity.ReaderTextAlign] ordinal; 0 = AUTO. */
    val readerTextAlign: Int = 0,
    /** User cover URL override. The local-file cover (customCoverPath) never travels — it's device-specific. */
    val customCoverUrl: String? = null,
    val overrideTitle: String? = null,
    val overrideAuthor: String? = null,
    val overrideDescription: String? = null,
    val overrideStatus: Int? = null,
    val overrideGenres: String? = null,
    val chapters: List<BackupChapter> = emptyList(),
)

@Serializable
data class BackupChapter(
    val url: String,
    val name: String,
    val uploadDate: Long = 0L,
    val chapterNumber: Float = -1f,
    val translator: String? = null,
    val read: Boolean = false,
    val readProgress: Float = 0f,
    val firstReadAt: Long? = null,
    val wordCount: Int = 0,
)
