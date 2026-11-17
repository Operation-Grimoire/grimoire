package io.grimoire.reading.state

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.storage.ReadingStateCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AcceptedReadingCommand internal constructor(val input: ReadingCompletionInput, internal val id: String)

sealed interface ReadingCommandOutcome {
    data class Saved(val save: ReadingStateSave) : ReadingCommandOutcome
    data object Missing : ReadingCommandOutcome
    data class Failed(val cause: Exception) : ReadingCommandOutcome
}

data class ReadingCommandAttempt(val accepted: AcceptedReadingCommand, val outcome: ReadingCommandOutcome)

/**
 * An owner's in-memory completion queue. Acceptance is synchronous; flush serializes preparation
 * and submission without holding the acceptance lock across IO. Prepared commands survive uncertain
 * replies/cancellation unchanged. An uncertain command blocks later input for its chapter in that
 * flush, while other chapters can proceed. A confirmed command with a failed side effect can be
 * retried without blocking newer intent. Automatic commands enter with their original writer and
 * evidence already bound. The queue owns no writer lifecycle, timers or crash outbox.
 */
class ReadingCompletionCommands(private val facade: ReadingStateFacade) {
    private class Pending(val accepted: AcceptedReadingCommand, var command: ReadingStateCommand?) {
        var confirmed = false
        var missing = false
    }
    private val lock = Any()
    private val flushes = Mutex()
    private val pending = mutableListOf<Pending>()
    private val latest = mutableMapOf<ChapterKey, AcceptedReadingCommand>()

    fun accept(input: ReadingCompletionInput): AcceptedReadingCommand {
        require(input !is ReadingCompletionInput.Visual && input !is ReadingCompletionInput.Audio)
        return enqueue(input, UUID.randomUUID().toString(), null)
    }

    fun acceptAutomatic(command: ReadingStateCommand): AcceptedReadingCommand {
        require(command.session != null && (command.input is ReadingCompletionInput.Visual || command.input is ReadingCompletionInput.Audio))
        return enqueue(command.input, command.id, command)
    }

    private fun enqueue(input: ReadingCompletionInput, id: String, command: ReadingStateCommand?) = synchronized(lock) {
        AcceptedReadingCommand(input, id).also {
            pending.add(Pending(it, command))
            latest[input.key] = it
        }
    }

    fun hasPending(): Boolean = synchronized(lock) { pending.isNotEmpty() }
    fun isLatest(accepted: AcceptedReadingCommand): Boolean = synchronized(lock) { latest[accepted.input.key] === accepted }

    /** A snapshot: input accepted during IO is handled by the next flush, never silently coalesced. */
    suspend fun flush(): List<ReadingCommandAttempt> = flushes.withLock {
        val batch = synchronized(lock) { pending.toList() }
        val blocked = mutableSetOf<ChapterKey>()
        val results = mutableListOf<ReadingCommandAttempt>()
        val finished = mutableListOf<Pending>()
        for (entry in batch) {
            currentCoroutineContext().ensureActive()
            val accepted = entry.accepted
            if (accepted.input.key in blocked) continue
            val outcome = try {
                val command = if (entry.missing) null else entry.command ?: facade.prepareExplicit(accepted.input, accepted.id).also { entry.command = it }
                if (command == null) {
                    entry.missing = true
                    finished.add(entry)
                    ReadingCommandOutcome.Missing
                } else {
                    val save = facade.submit(command)
                    if (save.confirmation is ReadingStateConfirmation.Confirmed) entry.confirmed = true
                    if (save.wordCount !is ReadingWordCountOutcome.Failed) finished.add(entry)
                    ReadingCommandOutcome.Saved(save)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (!entry.confirmed) blocked.add(accepted.input.key)
                ReadingCommandOutcome.Failed(failure)
            }
            results.add(ReadingCommandAttempt(accepted, outcome))
        }
        // Cancellation in a later entry must not lose earlier undelivered results. Leave those
        // commands available for receipt replay/current reconciliation on the next flush.
        currentCoroutineContext().ensureActive()
        synchronized(lock) { pending.removeAll(finished.toSet()) }
        results
    }
}
