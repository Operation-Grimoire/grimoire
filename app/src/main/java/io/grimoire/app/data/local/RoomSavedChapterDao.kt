package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.storage.*
import io.grimoire.app.data.local.entity.ChapterDownloadStateEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves portable identities and reads the version/body chunks in one Room transaction. */
@Singleton
class RoomSavedChapterDao @Inject constructor(private val database: AppDatabase) : SavedChapterDao {
    private val observationScope = UUID.randomUUID().toString()
    override suspend fun read(key: ChapterKey): StoredChapterContent? = readSnapshot(key)?.content

    override suspend fun readSnapshot(key: ChapterKey): SavedChapterSnapshot? = database.withTransaction {
        val dao = database.chapterDao()
        val chapter = dao.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
            ?: return@withTransaction null
        dao.getContent(chapter.id)?.let { content ->
            SavedChapterSnapshot(content, database.chapterDownloadStateDao().find(chapter.id)?.cacheRevision)
        }
    }

    override suspend fun readObserved(key: ChapterKey): ObservedSavedChapter = database.withTransaction {
        val chapters = database.chapterDao()
        val chapter = chapters.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
            ?: return@withTransaction ObservedSavedChapter(null, null)
        val states = database.chapterDownloadStateDao()
        val previous = states.find(chapter.id)
        val saved = chapters.getContent(chapter.id)?.let { SavedChapterSnapshot(it, previous?.cacheRevision) }
        // Preserve existing access/selection behavior, but never certify a locked subject.
        if (chapter.locked) return@withTransaction ObservedSavedChapter(saved, null)
        currentCoroutineContext().ensureActive()
        val state = previous ?: ChapterDownloadStateEntity(chapter.id, UUID.randomUUID().toString()).also { states.put(it) }
        currentCoroutineContext().ensureActive()
        ObservedSavedChapter(saved, ChapterStorageObservation(key, observationScope, state.revision,
            if (saved == null) ChapterContentPresence.ABSENT else ChapterContentPresence.SAVED))
    }

    override suspend fun isObservationCurrent(observation: ChapterStorageObservation): Boolean {
        if (observation.scope != observationScope || !database.isOpen) return false
        return database.withTransaction {
            val chapters = database.chapterDao()
            val key = observation.key
            val chapter = chapters.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
                ?: return@withTransaction false
            if (chapter.locked) return@withTransaction false
            val state = database.chapterDownloadStateDao().find(chapter.id) ?: return@withTransaction false
            state.revision == observation.generation &&
                (chapters.getContentVersionToken(chapter.id) != null) == (observation.presence == ChapterContentPresence.SAVED)
        }
    }
}
