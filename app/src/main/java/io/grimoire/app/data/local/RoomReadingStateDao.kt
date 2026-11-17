package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.app.data.local.entity.*
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReadingStateDao @Inject constructor(private val database: AppDatabase, private val saved: RoomSavedChapterDao) : ReadingStateDao {
    private val scope = UUID.randomUUID().toString()
    private suspend fun chapter(key: ChapterKey) = database.chapterDao().getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
    private fun value(key: ChapterKey, c: ChapterEntity) = ReadingCompletionState(key, if (c.read) ReadStatus.READ else ReadStatus.UNREAD, c.firstReadAt)
    private suspend fun record(key: ChapterKey, c: ChapterEntity) = database.readingStateStorageDao().find(c.id)?.let {
        decodeReadingStateRecord(it.state).also { r -> require(r.value == value(key, c)) { "Reading state and chapter disagree" } }
    }
    override suspend fun prepare(key: ChapterKey): PreparedReadingState? = database.withTransaction {
        val c = chapter(key) ?: return@withTransaction null
        val storage = database.readingStateStorageDao()
        val r = record(key, c) ?: ReadingStateRecord(UUID.randomUUID().toString(), 0, value(key, c)).also {
            storage.clearReceipts(c.id); storage.put(ReadingStateEntity(c.id, encodeReadingStateRecord(it)))
        }
        currentCoroutineContext().ensureActive()
        PreparedReadingState(ReadingStateBinding(key, scope, r.incarnation, r.intentRevision), r.value)
    }
    override suspend fun register(binding: ReadingStateBinding, content: ChapterStorageObservation, document: CanonicalDocument,
        kind: ReadingStateWriterKind): ReadingStateSession? = database.withTransaction {
        val c = chapter(binding.key) ?: return@withTransaction null
        val r = record(binding.key, c) ?: return@withTransaction null
        if (binding.scope != scope || binding.incarnation != r.incarnation || binding.intentRevision != r.intentRevision ||
            content.key != binding.key || !canRegisterReadingSession(content, document) || !saved.isObservationCurrent(content)) return@withTransaction null
        val s = ReadingStateSession(UUID.randomUUID().toString(), binding, content, document, kind)
        database.readingStateStorageDao().put(ReadingStateEntity(c.id, encodeReadingStateRecord(bindReadingStateWriter(r, s))))
        currentCoroutineContext().ensureActive(); s
    }
    override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit = database.withTransaction {
        val c = chapter(command.binding.key)
        val r = c?.let { record(command.binding.key, it) }
        val receipt = if (r?.incarnation == command.binding.incarnation)
            c?.let { database.readingStateStorageDao().receipt(it.id, command.id) }?.let { decodeReadingStateReceipt(it.receipt) } else null
        val transition = decideReadingState(command, r, receipt, command.binding.scope == scope,
            command.session?.let { saved.isObservationCurrent(it.content) } ?: false)
        if (transition.record != null) {
            val id = requireNotNull(c).id; val next = requireNotNull(transition.record)
            require(next.value.status != ReadStatus.UNKNOWN)
            val storage = database.readingStateStorageDao()
            storage.setValues(id, next.value.status == ReadStatus.READ, next.value.firstReadAt)
            // The legacy-write trigger deletes the old protocol; restore this command's decided record atomically.
            storage.put(ReadingStateEntity(id, encodeReadingStateRecord(next)))
            storage.putReceipt(ReadingStateReceiptEntity(id, command.id, encodeReadingStateReceipt(requireNotNull(transition.receipt))))
        }
        currentCoroutineContext().ensureActive(); transition.result
    }
    override suspend fun read(key: ChapterKey): ReadingCompletionState? = database.withTransaction {
        val c = chapter(key) ?: return@withTransaction null
        record(key, c) // Corrupt existing protocol must not be mistaken for absence.
        value(key, c)
    }
}
