package io.grimoire.reading.state

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

sealed interface ReadingStateConfirmation {
    /** Historical acknowledgement only; use ReadingStateSave.current for presentation. */
    data class Confirmed(val valueAtCommit: ReadingCompletionState, val replayed: Boolean) : ReadingStateConfirmation
    data class NotApplied(val reason: ReadingStateRejection) : ReadingStateConfirmation
}

sealed interface ReadingWordCountOutcome {
    data object NotApplicable : ReadingWordCountOutcome
    data object NotRequested : ReadingWordCountOutcome
    data object Completed : ReadingWordCountOutcome
    data class Failed(val cause: Exception) : ReadingWordCountOutcome
}

/** Current is reconciled after the side effect; null means the target no longer exists. */
data class ReadingStateSave(val confirmation: ReadingStateConfirmation, val current: ReadingCompletionState?,
    val wordCount: ReadingWordCountOutcome)

/**
 * Shared command preparation, confirmation and separately reported word-count backfill.
 * Callers retain an immutable command through any uncertain exception/cancellation and retry that
 * command, never prepare a replacement automatically. Hosts serialize their accepted explicit intents.
 * Backfill must be idempotent: a receipt retry may run it again. Null explicitly means this host has
 * no legacy word-count side effect. This facade neither writes position/history nor owns a crash outbox.
 */
class ReadingStateFacade(private val dao: ReadingStateDao, private val backfill: (suspend (ChapterKey) -> Unit)?) {
    /** Input already contains the timestamp captured at intent acceptance, before asynchronous work. */
    suspend fun prepareExplicit(input: ReadingCompletionInput, commandId: String = UUID.randomUUID().toString()): ReadingStateCommand? {
        require(input !is ReadingCompletionInput.Visual && input !is ReadingCompletionInput.Audio) { "Automatic input needs a registered writer" }
        require(commandId.isNotBlank()) { "Command identity is required" }
        val prepared = dao.prepare(input.key) ?: return null
        return ReadingStateCommand(commandId, prepared.binding, input, null, 0)
    }

    /** Sequence/evidence belong to the accepted sample; preparing a retry must not relabel either. */
    fun prepareAutomatic(session: ReadingStateSession, input: ReadingCompletionInput, sequence: Long,
        commandId: String = UUID.randomUUID().toString()): ReadingStateCommand {
        require(input is ReadingCompletionInput.Visual || input is ReadingCompletionInput.Audio)
        require(input.key == session.binding.key && commandId.isNotBlank() && sequence > 0)
        return ReadingStateCommand(commandId, session.binding, input, session, sequence)
    }

    suspend fun submit(command: ReadingStateCommand): ReadingStateSave {
        val committed = dao.commit(command)
        currentCoroutineContext().ensureActive()
        val confirmation = when (committed) {
            is ReadingStateCommit.Committed -> ReadingStateConfirmation.Confirmed(committed.valueAtCommit, committed.replayed)
            is ReadingStateCommit.NotApplied -> ReadingStateConfirmation.NotApplied(committed.reason)
        }
        // Legacy audio completion invokes backfill even if the existing read/timestamp are unchanged.
        val readAccepted = (committed is ReadingStateCommit.Committed && committed.valueAtCommit.status == ReadStatus.READ) ||
            (command.input is ReadingCompletionInput.Audio && committed == ReadingStateCommit.NotApplied(ReadingStateRejection.NO_CHANGE))
        val effect = when {
            backfill == null -> ReadingWordCountOutcome.NotApplicable
            !readAccepted -> ReadingWordCountOutcome.NotRequested
            else -> try {
                backfill.invoke(command.binding.key)
                ReadingWordCountOutcome.Completed
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { ReadingWordCountOutcome.Failed(failure) }
        }
        currentCoroutineContext().ensureActive()
        // Another accepted intent may have committed while backfill was suspended.
        val current = dao.read(command.binding.key)
        currentCoroutineContext().ensureActive()
        return ReadingStateSave(confirmation, current, effect)
    }
}
