package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.app.data.local.entity.*
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
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
    private data class ChapterTarget(val rowId: Long, val novelId: Long, val storedUrl: String, val binding: ReadingStateBinding)
    private data class WholeNovel(val rowId: Long, val key: NovelKey)
    private data class Batch(
        override val read: Boolean,
        val atMillis: Long,
        val id: String,
        val targets: List<ChapterTarget>,
        val wholeNovel: WholeNovel? = null,
    ) : PreparedReadingCompletionBatch

    private data class NovelBatch(
        override val intent: NovelReadingCompletionIntent,
        val batch: Batch,
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
            val batch = prepareWholeNovel(novel, intent.read, intent.atMillis, intent.id)
            currentCoroutineContext().ensureActive()
            NovelBatch(intent, batch)
        }

    override suspend fun prepareSelectedCompletion(intent: SelectedReadingCompletionIntent): List<PreparedReadingCompletionBatch> =
        database.withTransaction {
            val batches = when (val selection = intent.selection) {
                is ReadingCompletionSelection.Novels -> {
                    val ids = selection.rowIds.distinct()
                    val novels = novelsById(ids)
                    ids.mapIndexedNotNull { index, id ->
                        novels[id]?.let { prepareWholeNovel(it, intent.read, intent.atMillis, "${intent.id}:$index") }
                    }
                }
                is ReadingCompletionSelection.Chapters -> {
                    val ids = selection.rowIds.distinct()
                    val rows = selectedChapters(ids)
                    val novels = novelsById(rows.values.map { it.novelId }.distinct())
                    // Retain group boundaries even when a previously selected row is missing.
                    val groups = selection.chunkSize?.let { ids.chunked(it) } ?: listOf(ids)
                    groups.mapIndexedNotNull { index, group ->
                        val targets = group.mapNotNull { id ->
                            val chapter = rows[id] ?: return@mapNotNull null
                            if (selection.novelId != null && chapter.novelId != selection.novelId) return@mapNotNull null
                            val novel = checkNotNull(novels[chapter.novelId])
                            prepareTarget(NovelKey(novel.sourceId, novel.url), chapter)
                        }
                        if (targets.isEmpty()) null else Batch(intent.read, intent.atMillis, "${intent.id}:$index", targets)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            batches
        }

    private suspend fun prepareWholeNovel(novel: NovelEntity, read: Boolean, atMillis: Long, id: String): Batch {
        val key = NovelKey(novel.sourceId, novel.url)
        val targets = database.chapterDao().getChapterMetadataOnce(novel.id).map { prepareTarget(key, it) }
        return Batch(read, atMillis, id, targets, WholeNovel(novel.id, key))
    }

    private suspend fun prepareTarget(novel: NovelKey, chapter: ChapterEntity): ChapterTarget {
        // A legacy row without a usable URL still participates in bulk actions. Its receipt is
        // local to the row, which cannot be opened by this internal key.
        val key = ChapterKey(novel, chapter.url.ifBlank { "legacy-blank-chapter:${chapter.id}" })
        return ChapterTarget(chapter.id, chapter.novelId, chapter.url, prepareChapter(key, chapter).binding)
    }

    // Lookup chunking avoids SQLite's bind limit. It does not split a commit's transaction.
    private suspend fun selectedChapters(ids: List<Long>): Map<Long, ChapterEntity> =
        ids.chunked(999).flatMap { database.readingStateStorageDao().selectedChapters(it) }.associateBy { it.id }

    private suspend fun novelsById(ids: List<Long>): Map<Long, NovelEntity> =
        ids.chunked(999).flatMap { database.novelDao().getByIds(it) }.associateBy { it.id }

    override suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit =
        commitBatch((command as? NovelBatch)?.batch)

    override suspend fun commitSelectedCompletion(command: PreparedReadingCompletionBatch): NovelReadingCompletionCommit =
        commitBatch(command as? Batch)

    private suspend fun commitBatch(batch: Batch?): NovelReadingCompletionCommit = database.withTransaction {
        if (batch == null) return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.INVALID)
        val wholeNovel = batch.wholeNovel
        val novels = novelsById(wholeNovel?.let { listOf(it.rowId) } ?: batch.targets.map { it.novelId }.distinct())
        if (wholeNovel != null && !novels[wholeNovel.rowId].matches(wholeNovel.key)) {
            return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.STALE)
        }
        val chapters = if (wholeNovel != null) {
            database.chapterDao().getChapterMetadataOnce(wholeNovel.rowId).associateBy { it.id }
        } else selectedChapters(batch.targets.map { it.rowId })
        val writes = ArrayList<Pair<Long, ReadingStateTransition>>(batch.targets.size)
        var replayed = false
        for (target in batch.targets) {
            val chapter = chapters[target.rowId]
            if (chapter?.novelId != target.novelId || chapter.url != target.storedUrl ||
                !novels[target.novelId].matches(target.binding.key.novel)
            ) {
                return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.STALE)
            }
            val explicit = markReadingStateCommand(target.binding, batch.read, batch.atMillis, batch.id)
            val transition = decide(explicit, chapter)
            when (val result = transition.result) {
                is ReadingStateCommit.NotApplied -> return@withTransaction NovelReadingCompletionCommit.NotApplied(result.reason)
                is ReadingStateCommit.Committed -> {
                    if (result.replayed) replayed = true else writes.add(target.rowId to transition)
                }
            }
        }
        // A whole-novel first write requires the entire current membership. A receipt confirms
        // the original batch. A batch may never combine a replay with new writes.
        if ((replayed && writes.isNotEmpty()) || (!replayed && chapters.size != batch.targets.size)) {
            return@withTransaction NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.CONFLICT)
        }
        for ((rowId, transition) in writes) applyTransition(rowId, transition)
        currentCoroutineContext().ensureActive()
        NovelReadingCompletionCommit.Committed(replayed)
    }

    private fun NovelEntity?.matches(key: NovelKey): Boolean =
        this != null && sourceId == key.sourceId && url == key.url

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
