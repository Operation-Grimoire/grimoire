package io.grimoire.ui.library

/** Per-novel chapter counts shown on library badges. */
data class ChapterCounts(val total: Int, val readCount: Int, val downloadedCount: Int, val lockedCount: Int = 0)

/** Denominator for read progress; locked chapters are left out unless [includeLocked]. */
fun ChapterCounts.effectiveTotal(includeLocked: Boolean): Int =
    if (includeLocked) total else (total - lockedCount).coerceAtLeast(0)

fun ChapterCounts.readPercent(includeLocked: Boolean): Int {
    val denom = effectiveTotal(includeLocked)
    return if (denom > 0) (readCount * 100 / denom).coerceIn(0, 100) else 0
}
