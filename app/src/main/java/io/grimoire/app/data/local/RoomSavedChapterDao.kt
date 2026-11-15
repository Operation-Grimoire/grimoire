package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.storage.SavedChapterDao
import io.grimoire.reading.storage.SavedChapterSnapshot
import javax.inject.Inject

/** Resolves portable identities and reads the version/body chunks in one Room transaction. */
class RoomSavedChapterDao @Inject constructor(private val database: AppDatabase) : SavedChapterDao {
    override suspend fun read(key: ChapterKey): StoredChapterContent? = readSnapshot(key)?.content

    override suspend fun readSnapshot(key: ChapterKey): SavedChapterSnapshot? = database.withTransaction {
        val dao = database.chapterDao()
        val chapter = dao.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
            ?: return@withTransaction null
        dao.getContent(chapter.id)?.let { content ->
            SavedChapterSnapshot(content, database.chapterDownloadStateDao().find(chapter.id)?.cacheRevision)
        }
    }
}
