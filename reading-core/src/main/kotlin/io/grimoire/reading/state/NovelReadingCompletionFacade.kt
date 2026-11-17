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

    fun command(binding: ReadingStateBinding): ReadingStateCommand {
        require(binding.key.novel == novel)
        val input = if (read) ReadingCompletionInput.MarkRead(binding.key, atMillis)
            else ReadingCompletionInput.MarkUnread(binding.key)
        return ReadingStateCommand(id, binding, input, session = null, sequence = 0)
    }
}

/** An immutable batch of current chapter identities and intent revisions from a platform store. */
interface PreparedNovelReadingCompletion {
    val intent: NovelReadingCompletionIntent
}

sealed interface NovelReadingCompletionCommit {
    data class Committed(val replayed: Boolean) : NovelReadingCompletionCommit
    data class NotApplied(val reason: ReadingStateRejection) : NovelReadingCompletionCommit
}

interface NovelReadingCompletionDao {
    suspend fun prepareNovelCompletion(intent: NovelReadingCompletionIntent): PreparedNovelReadingCompletion?

    /** Commit the entire prepared batch or confirm its receipts. Never expand a replay's scope. */
    suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit
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

    suspend fun submit(command: PreparedNovelReadingCompletion): NovelReadingCompletionSave {
        val confirmation = dao.commitNovelCompletion(command)
        currentCoroutineContext().ensureActive()
        val wordCount = runReadingWordCountBackfill(
            confirmation is NovelReadingCompletionCommit.Committed && command.intent.read, backfill,
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
