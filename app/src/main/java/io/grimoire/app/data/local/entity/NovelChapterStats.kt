package io.grimoire.app.data.local.entity

data class NovelChapterStats(
    val novelId: Long,
    val total: Int,
    val readCount: Int,
    val downloadedCount: Int,
    val lockedCount: Int = 0,
)
