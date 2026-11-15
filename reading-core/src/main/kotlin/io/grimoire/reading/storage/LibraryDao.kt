package io.grimoire.reading.storage

import io.grimoire.reading.NovelKey

/** Display metadata and membership; device-local storage and category IDs remain private. */
data class LibraryEntry(
    val key: NovelKey,
    val title: String,
    val inLibrary: Boolean,
    val author: String? = null,
    val description: String? = null,
)

/**
 * Platform storage boundary for existing books. Null means the record is absent;
 * storage/access failures and cancellation propagate. Implementations own blocking
 * I/O dispatch and concurrency. These operations never fetch or create a book.
 */
interface LibraryDao {
    suspend fun find(key: NovelKey): LibraryEntry?

    /**
     * Set an explicit, idempotent value, changing only membership. Preserve book
     * metadata, categories, chapters, downloads and progress. Return the committed
     * record or null if absent. Cancellation after commit may prevent delivery of
     * the response; it does not imply that the write was rolled back.
     */
    suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry?
}
