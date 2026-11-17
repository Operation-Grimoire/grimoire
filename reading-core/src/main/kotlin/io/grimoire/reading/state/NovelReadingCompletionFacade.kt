package io.grimoire.reading.state

import io.grimoire.reading.NovelKey
import io.grimoire.reading.storage.ReadingStateBinding
import io.grimoire.reading.storage.ReadingStateCommand
import io.grimoire.reading.storage.ReadingStateRejection
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class NovelReadingCompletionIntent(
    val novel: NovelKey,
    val read: Boolean,
    val atMillis: Long,
    val id: String = UUID.randomUUID().toString(),
) {
    init {
        require(atMillis >= 0 && id.isNotBlank())
    }
}

sealed interface ReadingCompletionSelection {
    data class Novels(val rowIds: List<Long>) : ReadingCompletionSelection
    data class Chapters(val rowIds: List<Long>, val novelId: Long?, val chunkSize: Int?) : ReadingCompletionSelection {
        init { require(chunkSize == null || chunkSize > 0) }
    }
}

data class SelectedReadingCompletionIntent(
    val selection: ReadingCompletionSelection,
    val read: Boolean,
    val atMillis: Long,
    val id: String = UUID.randomUUID().toString(),
) {
    init { require(atMillis >= 0 && id.isNotBlank()) }
}

fun markReadingStateCommand(binding: ReadingStateBinding, read: Boolean, atMillis: Long, id: String): ReadingStateCommand {
    val input = if (read) ReadingCompletionInput.MarkRead(binding.key, atMillis)
        else ReadingCompletionInput.MarkUnread(binding.key)
    return ReadingStateCommand(id, binding, input, session = null, sequence = 0)
}

interface PreparedReadingCompletionBatch {
    val read: Boolean
}

/** An immutable batch of current chapter identities and intent revisions from a platform store. */
interface PreparedNovelReadingCompletion : PreparedReadingCompletionBatch {
    val intent: NovelReadingCompletionIntent
    override val read: Boolean get() = intent.read
}

sealed interface NovelReadingCompletionCommit {
    data class Committed(val replayed: Boolean) : NovelReadingCompletionCommit
    data class NotApplied(val reason: ReadingStateRejection) : NovelReadingCompletionCommit
}

interface NovelReadingCompletionDao {
    suspend fun prepareNovelCompletion(intent: NovelReadingCompletionIntent): PreparedNovelReadingCompletion?
    /** Freeze all groups together before any updates. Each group retains its own transaction boundary. */
    suspend fun prepareSelectedCompletion(intent: SelectedReadingCompletionIntent): List<PreparedReadingCompletionBatch>

    /** Commit the entire prepared batch or confirm its receipts. Never expand a replay's scope. */
    suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit
    suspend fun commitSelectedCompletion(command: PreparedReadingCompletionBatch): NovelReadingCompletionCommit
}

data class NovelReadingCompletionSave(
    val confirmation: NovelReadingCompletionCommit,
    val wordCount: ReadingWordCountOutcome,
)

class NovelReadingCompletionFacade(
    private val dao: NovelReadingCompletionDao,
    private val backfill: (suspend () -> Unit)?,
) {
    suspend fun prepare(intent: NovelReadingCompletionIntent): PreparedNovelReadingCompletion? =
        dao.prepareNovelCompletion(intent)

    suspend fun prepare(intent: SelectedReadingCompletionIntent): List<PreparedReadingCompletionBatch> =
        dao.prepareSelectedCompletion(intent)

    suspend fun submit(command: PreparedReadingCompletionBatch): NovelReadingCompletionSave {
        val confirmation = if (command is PreparedNovelReadingCompletion) dao.commitNovelCompletion(command)
            else dao.commitSelectedCompletion(command)
        currentCoroutineContext().ensureActive()
        val wordCount = runReadingWordCountBackfill(
            confirmation is NovelReadingCompletionCommit.Committed && command.read, backfill,
        )
        currentCoroutineContext().ensureActive()
        return NovelReadingCompletionSave(confirmation, wordCount)
    }

    /** Once the write is confirmed, a side-effect retry does not need the original chapters. */
    suspend fun retryWordCount(save: NovelReadingCompletionSave): NovelReadingCompletionSave {
        require(save.confirmation is NovelReadingCompletionCommit.Committed && save.wordCount is ReadingWordCountOutcome.Failed)
        currentCoroutineContext().ensureActive()
        val wordCount = runReadingWordCountBackfill(true, backfill)
        currentCoroutineContext().ensureActive()
        return save.copy(wordCount = wordCount)
    }
}
