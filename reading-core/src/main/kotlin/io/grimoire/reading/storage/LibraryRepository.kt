package io.grimoire.reading.storage

import io.grimoire.reading.NovelKey

/** Shared membership commands never infer a toggle from potentially stale state. */
class LibraryRepository(private val dao: LibraryDao) {
    suspend fun find(key: NovelKey): LibraryEntry? = dao.find(key)?.also { check(it.key == key) { "Library identity mismatch" } }

    suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? =
        dao.setMembership(key, inLibrary)?.also {
            check(it.key == key) { "Library identity mismatch" }
            check(it.inLibrary == inLibrary) { "Library membership was not confirmed" }
        }
}
