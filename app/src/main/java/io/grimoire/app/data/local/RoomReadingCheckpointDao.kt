package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.app.data.local.entity.ReadingCheckpointEntity
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.anchor.ReadingAnchor
import io.grimoire.reading.storage.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReadingCheckpointDao @Inject constructor(
    private val database: AppDatabase,
    private val saved: RoomSavedChapterDao,
) : ReadingCheckpointDao {
    override suspend fun register(observation: ChapterStorageObservation, document: CanonicalDocument): ReadingSession? = database.withTransaction {
        if (!canRegisterReadingSession(observation, document) || !saved.isObservationCurrent(observation)) return@withTransaction null
        val key = observation.key
        val chapter = requireNotNull(database.chapterDao().getByNovelUrl(key.novel.sourceId, key.novel.url, key.url))
        val previous = state(chapter.id, key)
        val session = ReadingSession(UUID.randomUUID().toString(), observation, document)
        database.readingCheckpointStateDao().put(ReadingCheckpointEntity(chapter.id,
            encodeReadingCheckpoint(ReadingCheckpointState(session.binding, anchor = previous?.anchor))))
        currentCoroutineContext().ensureActive()
        session
    }

    override suspend fun commit(command: CheckpointCommand): CheckpointCommit = database.withTransaction {
        val key = command.session.observation.key
        val chapter = database.chapterDao().getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
        val previous = chapter?.let { state(it.id, key) }
        val result = decideCheckpoint(command, previous, saved.isObservationCurrent(command.session.observation))
        if (result is CheckpointCommit.Committed && !result.replayed) {
            database.readingCheckpointStateDao().put(ReadingCheckpointEntity(requireNotNull(chapter).id,
                encodeReadingCheckpoint(ReadingCheckpointState(command.session.binding, command.sequence, command.anchor))))
        }
        currentCoroutineContext().ensureActive()
        result
    }

    override suspend fun read(key: ChapterKey): ReadingAnchor? = database.withTransaction {
        val chapter = database.chapterDao().getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
            ?: return@withTransaction null
        if (chapter.locked) return@withTransaction null
        state(chapter.id, key)?.anchor
    }

    private suspend fun state(id: Long, key: ChapterKey): ReadingCheckpointState? =
        database.readingCheckpointStateDao().find(id)?.let { decodeReadingCheckpoint(it.state) }?.also {
            require(it.binding.observation.key == key) { "Checkpoint identity mismatch" }
        }
}
