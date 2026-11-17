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
class RoomReadingStateDao @Inject constructor(
    private val database: AppDatabase,
    private val saved: RoomSavedChapterDao,
) : ReadingStateDao, NovelReadingCompletionDao {
    private data class NovelTarget(val rowId: Long, val storedUrl: String, val binding: ReadingStateBinding)
    private data class NovelBatch(
        override val intent: NovelReadingCompletionIntent,
        val novelId: Long,
        val targets: List<NovelTarget>,
    ) : PreparedNovelReadingCompletion

    private val scope = UUID.randomUUID().toString()
    private suspend fun chapter(key: ChapterKey) = database.chapterDao().getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
    private fun value(key: ChapterKey, c: ChapterEntity) = ReadingCompletionState(key, if (c.read) ReadStatus.READ else ReadStatus.UNREAD, c.firstReadAt)
    private suspend fun record(key: ChapterKey, c: ChapterEntity) = database.readingStateStorageDao().find(c.id)?.let {
        decodeReadingStateRecord(it.state).also { r -> require(r.value == value(key, c)) { "Reading state and chapter disagree" } }
    }
    override suspend fun prepare(key: ChapterKey): PreparedReadingState? = database.withTransaction {
        val c = chapter(key) ?: return@withTransaction null
        val prepared = prepareChapter(key, c)
        currentCoroutineContext().ensureActive()
        prepared
    }

    private suspend fun prepareChapter(key: ChapterKey, chapter: ChapterEntity): PreparedReadingState {
        val storage = database.readingStateStorageDao()
        val r = record(key, chapter) ?: ReadingStateRecord(UUID.randomUUID().toString(), 0, value(key, chapter)).also {
            storage.clearReceipts(chapter.id)
            storage.put(ReadingStateEntity(chapter.id, encodeReadingStateRecord(it)))
        }
        return PreparedReadingState(ReadingStateBinding(key, scope, r.incarnation, r.intentRevision), r.value)
    }

    override suspend fun prepareNovelCompletion(intent: NovelReadingCompletionIntent): PreparedNovelReadingCompletion? =
        database.withTransaction {
            val novel = database.novelDao().getBySourceUrl(intent.novel.sourceId, intent.novel.url)
                ?: return@withTransaction null
            val targets = database.chapterDao().getChapterMetadataOnce(novel.id).map { chapter ->
                // Legacy rows without a usable URL still participate in mark-all. Their receipt
                // is local to the row, which cannot be opened by this internal key.
                val key = ChapterKey(intent.novel, chapter.url.ifBlank { "legacy-blank-chapter:${chapter.id}" })
                NovelTarget(chapter.id, chapter.url, prepareChapter(key, chapter).binding)
            }
            currentCoroutineContext().ensureActive()
            NovelBatch(intent, novel.id, targets)
        }

    override suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit =
        database.withTransaction {
            val batch = command as? NovelBatch
                ?: return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.INVALID)
            val novel = database.novelDao().getBySourceUrl(batch.intent.novel.sourceId, batch.intent.novel.url)
            if (novel?.id != batch.novelId) {
                return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.STALE)
            }
            val chapters = database.chapterDao().getChapterMetadataOnce(batch.novelId).associateBy { it.id }
            val writes = ArrayList<Pair<Long, ReadingStateTransition>>(batch.targets.size)
            var replayed = false
            for (target in batch.targets) {
                val chapter = chapters[target.rowId]
                if (chapter?.url != target.storedUrl) {
                    return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.STALE)
                }
                val explicit = batch.intent.command(target.binding)
                val transition = decide(explicit, chapter)
                when (val result = transition.result) {
                    is ReadingStateCommit.NotApplied -> return@withTransaction NovelReadingCompletionCommit.NotApplied(result.reason)
                    is ReadingStateCommit.Committed -> {
                        if (result.replayed) replayed = true else writes.add(target.rowId to transition)
                    }
                }
            }
            // First writes require the entire current membership. A receipt confirms the original batch;
            // later chapters are outside its scope. A batch may never combine a replay with new writes.
            if ((replayed && writes.isNotEmpty()) || (!replayed && chapters.size != batch.targets.size)) {
                return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.CONFLICT)
            }
            for ((rowId, transition) in writes) applyTransition(rowId, transition)
            currentCoroutineContext().ensureActive()
            NovelReadingCompletionCommit.Committed(replayed)
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
    override suspend fun retire(session: ReadingStateSession): Boolean = database.withTransaction {
        val c = chapter(session.binding.key) ?: return@withTransaction false
        val next = retireReadingStateWriter(record(session.binding.key, c), session, session.binding.scope == scope)
            ?: return@withTransaction false
        database.readingStateStorageDao().put(ReadingStateEntity(c.id, encodeReadingStateRecord(next)))
        currentCoroutineContext().ensureActive(); true
    }
    override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit = database.withTransaction {
        val c = chapter(command.binding.key)
        val transition = decide(command, c)
        if (transition.record != null) applyTransition(requireNotNull(c).id, transition)
        currentCoroutineContext().ensureActive()
        transition.result
    }

    private suspend fun decide(command: ReadingStateCommand, c: ChapterEntity?): ReadingStateTransition {
        val r = c?.let { record(command.binding.key, it) }
        val receipt = if (r?.incarnation == command.binding.incarnation)
            c?.let { database.readingStateStorageDao().receipt(it.id, command.id) }?.let { decodeReadingStateReceipt(it.receipt) } else null
        return decideReadingState(command, r, receipt, command.binding.scope == scope,
            command.session?.let { saved.isObservationCurrent(it.content) } ?: false)
    }

    private suspend fun applyTransition(id: Long, transition: ReadingStateTransition) {
        val next = requireNotNull(transition.record)
        val receipt = requireNotNull(transition.receipt)
        require(next.value.status != ReadStatus.UNKNOWN)
        val storage = database.readingStateStorageDao()
        storage.setValues(id, next.value.status == ReadStatus.READ, next.value.firstReadAt)
        // The legacy-write trigger deletes the old protocol; restore this command's decided record atomically.
        storage.put(ReadingStateEntity(id, encodeReadingStateRecord(next)))
        storage.putReceipt(ReadingStateReceiptEntity(id, receipt.commandId, encodeReadingStateReceipt(receipt)))
    }
    override suspend fun read(key: ChapterKey): ReadingCompletionState? = database.withTransaction {
        val c = chapter(key) ?: return@withTransaction null
        record(key, c) // Corrupt existing protocol must not be mistaken for absence.
        value(key, c)
    }
}
