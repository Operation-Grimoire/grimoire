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
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

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
