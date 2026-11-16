package io.grimoire.reading.storage

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.StoredChapterContent

/**
 * Platform storage boundary for saved chapter bodies. Keys are source identities,
 * not database row IDs or filesystem paths. Implementations retain their own
 * integrity/access checks and return null only when no saved record exists.
 * Corruption, unsupported metadata, I/O errors and cancellation must propagate.
 * Implementations move blocking storage I/O off the caller's coroutine context.
 * Reading never rewrites content or retrieves replacements. read/readSnapshot are read-only;
 * readObserved may initialize separate protocol identity as documented below.
 */
fun interface SavedChapterDao {
    suspend fun read(key: ChapterKey): StoredChapterContent?

    /** Optional cache identity must be read in the same snapshot as the body. Null retains legacy cache paths. */
    suspend fun readSnapshot(key: ChapterKey): SavedChapterSnapshot? = read(key)?.let { SavedChapterSnapshot(it) }

    /**
     * Observe body and generation together, before asynchronous source access. Implementations
     * may allocate protocol identity without changing content, cache identity or reader state.
     * The compatibility default deliberately supplies no observation, including for absence.
     */
    suspend fun readObserved(key: ChapterKey): ObservedSavedChapter = ObservedSavedChapter(readSnapshot(key), null)

    /**
     * Read-only freshness check; never allocate/refresh identity while validating a witness.
     * A true result is only a point-in-time observation. Future checkpoint writes must compare
     * generation and session inside their own commit transaction, not rely on this result.
     */
    suspend fun isObservationCurrent(observation: ChapterStorageObservation): Boolean = false
}

data class SavedChapterSnapshot(val content: StoredChapterContent, val cacheRevision: String? = null)
