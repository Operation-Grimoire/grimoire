package io.grimoire.reading.storage

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.StoredChapterContent

/**
 * Platform storage boundary for saved chapter bodies. Keys are source identities,
 * not database row IDs or filesystem paths. Implementations retain their own
 * integrity/access checks and return null only when no saved record exists.
 * Corruption, unsupported metadata, I/O errors and cancellation must propagate.
 * Implementations move blocking storage I/O off the caller's coroutine context.
 * Reading never rewrites the record or retrieves replacement content.
 */
fun interface SavedChapterDao {
    suspend fun read(key: ChapterKey): StoredChapterContent?

    /** Optional cache identity must be read in the same snapshot as the body. Null retains legacy cache paths. */
    suspend fun readSnapshot(key: ChapterKey): SavedChapterSnapshot? = read(key)?.let { SavedChapterSnapshot(it) }
}

data class SavedChapterSnapshot(val content: StoredChapterContent, val cacheRevision: String? = null)
