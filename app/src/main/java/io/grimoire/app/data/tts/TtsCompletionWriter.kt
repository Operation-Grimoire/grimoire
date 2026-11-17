package io.grimoire.app.data.tts

import io.grimoire.app.data.reading.CompletionSaveNotice
import io.grimoire.reading.state.AcceptedReadingCommand
import io.grimoire.reading.state.AudioCompletionContext
import io.grimoire.reading.state.AudioCompletionEvidence
import io.grimoire.reading.state.ReadingCommandOutcome
import io.grimoire.reading.state.ReadingCompletionCommands
import io.grimoire.reading.state.ReadingCompletionInput
import io.grimoire.reading.state.ReadingStateConfirmation
import io.grimoire.reading.state.ReadingStateFacade
import io.grimoire.reading.state.ReadingWordCountOutcome
import io.grimoire.reading.storage.ReadingStateDao
import io.grimoire.reading.storage.ReadingStateRejection
import io.grimoire.reading.storage.ReadingStateSession
import io.grimoire.reading.storage.ReadingStateWriterKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-thread playback authority and retry queue. Transport owns which callback is current. */
internal class TtsCompletionWriter(
    private val scope: CoroutineScope,
    private val dao: ReadingStateDao,
    private val facade: ReadingStateFacade,
) {
    private val commands = ReadingCompletionCommands(facade)
    private val lifecycle = Mutex()
    private val retiring = linkedSetOf<ReadingStateSession>()
    private val failed = mutableSetOf<AcceptedReadingCommand>()
    private var generation = 0L
    private var writer: ReadingStateSession? = null
    private var submitted: Job? = null
    private val _notice = MutableStateFlow<CompletionSaveNotice?>(null)
    val notice = _notice.asStateFlow()

    suspend fun bind(input: TtsChapterInput) {
        val document = input.document ?: return
        val observation = input.observation ?: return
        if (document.key != observation.key) return
        val ticket = generation
        // Registration can return after the playback task is cancelled. Keep ownership of that
        // response in the manager scope so a detached session gets retired.
        scope.async {
            lifecycle.withLock {
                try {
                    if (ticket != generation) return@withLock
                    val binding = dao.prepare(document.key)?.binding ?: return@withLock
                    if (ticket != generation) return@withLock
                    val session = dao.register(binding, observation, document, ReadingStateWriterKind.AUDIO)
                        ?: return@withLock
                    if (ticket == generation) {
                        writer = session
                    } else {
                        retiring.add(session)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (ticket == generation) _notice.value = CompletionSaveNotice.CHANGED
                } finally {
                    // Exact retirement can retry independently. A failed retirement of the old
                    // writer must not prevent the replacement playback from getting authority.
                    tryRetirePending()
                }
            }
        }.await()
    }

    fun retire() {
        generation++
        writer?.let(retiring::add)
        writer = null
        submitted = null
        if (retiring.isNotEmpty()) scope.launch { retireWithNotice() }
    }

    fun complete(callback: TtsUtteranceCallbacks.Finished, index: Int, count: Int): Job? {
        val active = writer ?: return null
        if (!callback.completedWhilePlaying || count <= 0 || index != count - 1) return null
        submitted?.let { return it }
        val evidence = AudioCompletionEvidence(active.id, active.content, active.document.logicalRevision,
            count, index, engineCompleted = true)
        val current = AudioCompletionContext(active.id, active.content, active.document, count,
            playing = true, observationCurrent = true)
        val input = ReadingCompletionInput.Audio(active.binding.key, evidence, current, callback.atMillis)
        commands.acceptAutomatic(facade.prepareAutomatic(active, input, 1))
        // A replaced playback task must not cancel a command already accepted by this writer.
        return scope.launch { flush() }.also { submitted = it }
    }

    fun dismiss() { _notice.value = null }

    fun retry(): Job = scope.launch {
        flush()
    }

    private suspend fun flush() {
        // Finish detached writers before retrying their commands. A live writer can still
        // complete independently if cleanup of a different writer encounters a failure.
        retireWithNotice()
        val attempts = commands.flush { session -> session !in retiring }
        val retirementFailed = retiring.isNotEmpty()
        if (attempts.isEmpty() && _notice.value == CompletionSaveNotice.CHANGED) return
        val notices = attempts.mapNotNull { attempt ->
            val retry = failed.remove(attempt.accepted)
            val notice = when (val outcome = attempt.outcome) {
                is ReadingCommandOutcome.Failed -> CompletionSaveNotice.SAVE_FAILED
                ReadingCommandOutcome.Missing -> CompletionSaveNotice.CHANGED
                is ReadingCommandOutcome.Saved -> {
                    val reason = (outcome.save.confirmation as? ReadingStateConfirmation.NotApplied)?.reason
                    val audio = attempt.accepted.input as ReadingCompletionInput.Audio
                    val retired = writer?.id != audio.evidence.playbackId
                    when {
                        outcome.save.wordCount is ReadingWordCountOutcome.Failed -> CompletionSaveNotice.WORD_COUNT_FAILED
                        reason == ReadingStateRejection.INVALID || reason == ReadingStateRejection.CONFLICT ||
                            ((retry || retired) && reason == ReadingStateRejection.STALE) -> CompletionSaveNotice.CHANGED
                        else -> null
                    }
                }
            }
            if (notice == CompletionSaveNotice.SAVE_FAILED || notice == CompletionSaveNotice.WORD_COUNT_FAILED)
                failed.add(attempt.accepted)
            commands.acknowledgeDelivery(attempt.accepted)
            notice
        }
        _notice.value = if (retirementFailed) CompletionSaveNotice.SAVE_FAILED else notices.minByOrNull { it.ordinal }
    }

    private suspend fun retireWithNotice() {
        lifecycle.withLock { tryRetirePending() }
    }

    private suspend fun tryRetirePending() {
        try {
            retirePending()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _notice.value = CompletionSaveNotice.SAVE_FAILED
        }
    }

    private suspend fun retirePending() {
        while (retiring.isNotEmpty()) {
            val session = retiring.first()
            dao.retire(session)
            retiring.remove(session)
        }
    }
}
