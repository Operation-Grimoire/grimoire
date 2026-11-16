package io.grimoire.reading.storage

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.decodeChapterContent

/** Shared interpretation of saved content; source/network fallback belongs to the caller. */
class SavedChapterRepository(private val dao: SavedChapterDao) {
    suspend fun read(key: ChapterKey): List<NovelPage>? = readSnapshot(key)?.pages

    suspend fun readSnapshot(key: ChapterKey): SavedChapterPages? = dao.readSnapshot(key)?.let {
        SavedChapterPages(decodeChapterContent(it.content), it.cacheRevision)
    }

    suspend fun readObserved(key: ChapterKey): SavedChapterRead {
        val result = dao.readObserved(key)
        require(result.observation == null || result.observation.key == key) { "Observation chapter identity mismatch" }
        return SavedChapterRead(result.saved?.let { decodeChapterContent(it.content) },
            result.saved?.cacheRevision, result.observation)
    }

    suspend fun isObservationCurrent(observation: ChapterStorageObservation): Boolean = dao.isObservationCurrent(observation)
}

data class SavedChapterPages(val pages: List<NovelPage>, val cacheRevision: String?)

data class SavedChapterRead(
    val pages: List<NovelPage>?,
    val cacheRevision: String?,
    val observation: ChapterStorageObservation?,
)
