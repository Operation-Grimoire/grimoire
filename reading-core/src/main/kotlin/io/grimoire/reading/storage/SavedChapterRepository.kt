package io.grimoire.reading.storage

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.decodeChapterContent

/** Shared interpretation of saved content; source/network fallback belongs to the caller. */
class SavedChapterRepository(private val dao: SavedChapterDao) {
    suspend fun read(key: ChapterKey): List<NovelPage>? = dao.read(key)?.let(::decodeChapterContent)
}
