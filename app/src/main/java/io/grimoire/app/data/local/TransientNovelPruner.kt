package io.grimoire.app.data.local

import io.grimoire.app.data.local.dao.NovelDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes browse-and-bail novels the DB accumulates: opening any source novel
 * upserts its row (and chapter list) with favorite=0, and nothing else ever
 * cleans them up. We keep such a row only briefly — long enough that re-opening
 * during a session is cheap — then drop it once it's stale with no fully-read
 * chapter and no download. Favorites, anything with a chapter marked read, and
 * anything downloaded are never touched (see [NovelDao.pruneTransient]). A
 * partial read percentage does not protect a row — only `read = 1`.
 */
@Singleton
class TransientNovelPruner @Inject constructor(
    private val novelDao: NovelDao,
) {
    /** Run a prune pass; returns how many novels were removed. */
    suspend fun prune(now: Long = System.currentTimeMillis()): Int =
        novelDao.pruneTransient(cutoff = now - STALE_AFTER_MS)

    /**
     * Remove every eligible browse row right now, ignoring the grace window — backs
     * the manual "clear browse data" action. Protection is unchanged: favorites,
     * read chapters, and downloads still survive. Returns the count removed.
     */
    suspend fun clearAll(): Int = novelDao.pruneTransient(cutoff = Long.MAX_VALUE)

    companion object {
        /** Grace window before an untouched, progress-less browse row is dropped. */
        const val STALE_AFTER_MS: Long = 60L * 60L * 1000L // 1 hour
    }
}
