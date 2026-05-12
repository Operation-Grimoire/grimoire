package io.grimoire.app.data.local.entity

data class ReadingStats(
    val chaptersRead: Int,
    val wordsRead: Long,
    val novelsStarted: Int,
    val novelsCompleted: Int,
    val firstReadAt: Long?,
    val lastReadAt: Long?,
)

data class LibraryStats(
    val favoriteNovels: Int,
    val libraryChapters: Int,
    val libraryUnreadChapters: Int,
    val downloadedChapters: Int,
)
