package io.grimoire.app.data.local.entity

data class NovelChapterStats(
    val novelId: Long,
    val total: Int,
    val readCount: Int,
    val downloadedCount: Int,
    val lockedCount: Int = 0,
)

/**
 * Chapter count used as the denominator for read-progress UIs. When the user has set
 * `LibraryPreferences.includeLockedInTotals = false`, locked (paid / unavailable) chapters
 * are subtracted so the % reflects only chapters they can actually read.
 */
fun NovelChapterStats.effectiveTotal(includeLocked: Boolean): Int =
    if (includeLocked) total else (total - lockedCount).coerceAtLeast(0)

fun NovelChapterStats.readPercent(includeLocked: Boolean): Int {
    val denom = effectiveTotal(includeLocked)
    return if (denom > 0) (readCount * 100 / denom).coerceIn(0, 100) else 0
}
