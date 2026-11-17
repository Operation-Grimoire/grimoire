package io.grimoire.reading

import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ExplicitReadingCommandsTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/first")
    private val other = key.copy(url = "/second")
    private inner class Store : ReadingStateDao {
        val records = mutableMapOf(key to record(key), other to record(other))
        val receipts = mutableMapOf<String, ReadingStateReceipt>()
        val commands = mutableListOf<ReadingStateCommand>()
        var prepares = 0
        var failKey: ChapterKey? = null
        var loseReply = false
        var afterCommit: (suspend () -> Unit)? = null
        private fun record(k: ChapterKey) = ReadingStateRecord("incarnation-${k.url}", 0, ReadingCompletionState(k, ReadStatus.UNREAD, null))
        override suspend fun prepare(key: ChapterKey): PreparedReadingState? {
            prepares++
            return records[key]?.let { PreparedReadingState(ReadingStateBinding(key, "owner", it.incarnation, it.intentRevision), it.value) }
        }
        override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit {
            commands.add(command)
            if (command.binding.key == failKey) throw IOException("Before commit")
            val next = decideReadingState(command, records[command.binding.key], receipts[command.id], true, false)
            next.record?.let { records[command.binding.key] = it; receipts[command.id] = next.receipt!! }
            afterCommit?.invoke()
            if (loseReply) { loseReply = false; throw IOException("Lost reply") }
            return next.result
        }
        override suspend fun read(key: ChapterKey) = records[key]?.value
        override suspend fun register(binding: ReadingStateBinding, content: ChapterStorageObservation, document: CanonicalDocument,
            kind: ReadingStateWriterKind): ReadingStateSession? = error("Explicit queue must not register automatic writers")
        override suspend fun retire(session: ReadingStateSession): Boolean = error("No automatic writer")
    }

    @Test fun rapidAcceptedInputsPrepareInOrderAndPreserveFirstReadTime() = runBlocking<Unit> {
        val store = Store(); val queue = ReadingCompletionCommands(ReadingStateFacade(store, null))
        val read = queue.accept(ReadingCompletionInput.MarkRead(key, 100))
        val unread = queue.accept(ReadingCompletionInput.MarkUnread(key))
        val reread = queue.accept(ReadingCompletionInput.MarkRead(key, 900))
        assertFalse(queue.isLatest(read)); assertFalse(queue.isLatest(unread)); assertTrue(queue.isLatest(reread))
        val outcomes = queue.flush()
        assertEquals(listOf(read, unread, reread), outcomes.map { it.accepted })
        assertTrue(outcomes.all { it.outcome is ReadingCommandOutcome.Saved })
        assertEquals(listOf(0L, 1L, 2L), store.commands.map { it.binding.intentRevision })
        assertEquals(ReadingCompletionState(key, ReadStatus.READ, 100), store.read(key))
        assertEquals(3, store.receipts.size); assertFalse(queue.hasPending())
    }

    @Test fun uncertainReadBlocksOnlyItsChapterAndRetriesIdenticalBeforeUnread() = runBlocking<Unit> {
        val store = Store(); val queue = ReadingCompletionCommands(ReadingStateFacade(store, null))
        queue.accept(ReadingCompletionInput.MarkRead(key, 100)); queue.accept(ReadingCompletionInput.MarkUnread(key))
        queue.accept(ReadingCompletionInput.MarkRead(other, 200))
        store.loseReply = true
        val first = queue.flush()
        assertTrue(first[0].outcome is ReadingCommandOutcome.Failed); assertEquals(2, first.size)
        assertEquals(ReadStatus.READ, store.read(other)!!.status); assertEquals(2, store.prepares)
        val original = store.commands.first()
        val second = queue.flush()
        assertSame(original, store.commands[2]); assertEquals(3, store.prepares)
        assertTrue(((second[0].outcome as ReadingCommandOutcome.Saved).save.confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertEquals(ReadStatus.UNREAD, store.read(key)!!.status); assertEquals(100L, store.read(key)!!.firstReadAt)
        assertFalse(queue.hasPending()); assertEquals(3, store.receipts.size)
    }

    @Test fun partialBackfillAllowsLaterUnreadAndOldRetryNeverPublishesOldRead() = runBlocking<Unit> {
        val store = Store(); var broken = true
        val queue = ReadingCompletionCommands(ReadingStateFacade(store) { if (broken) throw IOException("Backfill") })
        val read = queue.accept(ReadingCompletionInput.MarkRead(key, 100))
        queue.accept(ReadingCompletionInput.MarkUnread(key))
        val first = queue.flush()
        assertTrue((first[0].outcome as ReadingCommandOutcome.Saved).save.wordCount is ReadingWordCountOutcome.Failed)
        assertEquals(ReadStatus.UNREAD, store.read(key)!!.status); assertTrue(queue.hasPending()); assertFalse(queue.isLatest(read))
        broken = false
        val retry = (queue.flush().single().outcome as ReadingCommandOutcome.Saved).save
        assertEquals(ReadStatus.READ, (retry.confirmation as ReadingStateConfirmation.Confirmed).valueAtCommit.status)
        assertEquals(ReadStatus.UNREAD, retry.current!!.status); assertSame(store.commands[0], store.commands[2])
        assertEquals(2, store.prepares); assertFalse(queue.hasPending())
    }

    @Test fun cancellationAfterCommitRetainsExactCommandForReceiptRetry() = runBlocking<Unit> {
        val store = Store(); val queue = ReadingCompletionCommands(ReadingStateFacade(store, null))
        queue.accept(ReadingCompletionInput.MarkRead(key, 100))
        val reached = CompletableDeferred<Unit>()
        store.afterCommit = { reached.complete(Unit); awaitCancellation() }
        val job = launch { queue.flush() }
        reached.await(); job.cancelAndJoin()
        assertTrue(queue.hasPending()); assertEquals(1, store.receipts.size)
        store.afterCommit = null
        val retried = (queue.flush().single().outcome as ReadingCommandOutcome.Saved).save
        assertTrue((retried.confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertSame(store.commands[0], store.commands[1]); assertEquals(1, store.prepares); assertFalse(queue.hasPending())
    }

    @Test fun cancellationInLaterEntryRetainsEarlierSaveForCurrentReconciliation() = runBlocking<Unit> {
        val store = Store(); val facade = ReadingStateFacade(store, null); val queue = ReadingCompletionCommands(facade)
        val first = queue.accept(ReadingCompletionInput.MarkRead(key, 100))
        queue.accept(ReadingCompletionInput.MarkRead(other, 200))
        val reached = CompletableDeferred<Unit>()
        store.afterCommit = { if (store.commands.size == 2) { reached.complete(Unit); awaitCancellation() } }
        val job = launch { queue.flush() }; reached.await(); job.cancelAndJoin()
        store.afterCommit = null
        facade.submit(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key))!!)
        val attempts = queue.flush()
        assertEquals(2, attempts.size); assertSame(first, attempts[0].accepted)
        val save = (attempts[0].outcome as ReadingCommandOutcome.Saved).save
        assertTrue((save.confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertEquals(ReadStatus.READ, save.confirmation.valueAtCommit.status)
        assertEquals(ReadStatus.UNREAD, save.current!!.status)
        assertSame(store.commands[0], store.commands[3]); assertSame(store.commands[1], store.commands[4])
        assertEquals(3, store.prepares); assertEquals(3, store.receipts.size); assertFalse(queue.hasPending())
    }

    @Test fun competingIntentRejectsOldUnknownCommandWithoutRepreparation() = runBlocking<Unit> {
        val store = Store(); val facade = ReadingStateFacade(store, null); val queue = ReadingCompletionCommands(facade)
        queue.accept(ReadingCompletionInput.MarkRead(key, 100)); store.failKey = key
        assertTrue(queue.flush().single().outcome is ReadingCommandOutcome.Failed)
        val old = store.commands.single(); store.failKey = null
        facade.submit(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key))!!)
        val result = (queue.flush().single().outcome as ReadingCommandOutcome.Saved).save
        assertEquals(ReadingStateConfirmation.NotApplied(ReadingStateRejection.CONFLICT), result.confirmation)
        assertSame(old, store.commands.last()); assertEquals(2, store.prepares)
        assertEquals(ReadStatus.UNREAD, result.current!!.status); assertFalse(queue.hasPending())
    }

    @Test fun undeliveredMissingResultCannotBindARecreatedTargetAfterCancellation() = runBlocking<Unit> {
        val store = Store(); val queue = ReadingCompletionCommands(ReadingStateFacade(store, null))
        val old = store.records.remove(key)!!
        val missing = queue.accept(ReadingCompletionInput.MarkRead(key, 100))
        queue.accept(ReadingCompletionInput.MarkRead(other, 200))
        val reached = CompletableDeferred<Unit>()
        store.afterCommit = { reached.complete(Unit); awaitCancellation() }
        val job = launch { queue.flush() }; reached.await(); job.cancelAndJoin()
        store.afterCommit = null; store.records[key] = old.copy(incarnation = "replacement")
        val results = queue.flush()
        assertEquals(2, results.size); assertSame(missing, results[0].accepted)
        assertEquals(ReadingCommandOutcome.Missing, results[0].outcome)
        assertEquals(ReadStatus.UNREAD, store.read(key)!!.status)
        assertEquals(2, store.prepares); assertEquals(1, store.receipts.size); assertFalse(queue.hasPending())
    }

    @Test fun newAcceptanceDuringSuspendedFlushIsPreservedForNextFlush() = runBlocking<Unit> {
        val store = Store(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val queue = ReadingCompletionCommands(ReadingStateFacade(store) { entered.complete(Unit); release.await() })
        val read = queue.accept(ReadingCompletionInput.MarkRead(key, 100))
        val flushing = async { queue.flush() }; entered.await()
        val unread = queue.accept(ReadingCompletionInput.MarkUnread(key)); assertFalse(queue.isLatest(read))
        release.complete(Unit); assertEquals(read, flushing.await().single().accepted); assertTrue(queue.hasPending())
        assertEquals(unread, queue.flush().single().accepted); assertEquals(ReadStatus.UNREAD, store.read(key)!!.status)
        assertFalse(queue.hasPending())
    }

    @Test fun missingTargetIsTerminalAndOtherChapterContinues() = runBlocking<Unit> {
        val store = Store(); val queue = ReadingCompletionCommands(ReadingStateFacade(store, null))
        queue.accept(ReadingCompletionInput.MarkRead(key, 100)); queue.accept(ReadingCompletionInput.MarkRead(other, 200))
        store.records.remove(key)
        val attempts = queue.flush()
        assertEquals(ReadingCommandOutcome.Missing, attempts[0].outcome)
        assertEquals(ReadStatus.READ, store.read(other)!!.status); assertFalse(queue.hasPending())
    }

    @Test fun nextKeepsAcceptedTimeAndNeverMarksItsSuccessor() = runBlocking<Unit> {
        val store = Store(); val queue = ReadingCompletionCommands(ReadingStateFacade(store, null))
        queue.accept(ReadingCompletionInput.Next(key, key, true, 100)); store.failKey = key
        queue.flush(); store.failKey = null; queue.flush()
        assertEquals(ReadingCompletionState(key, ReadStatus.READ, 100), store.read(key))
        assertEquals(ReadStatus.UNREAD, store.read(other)!!.status)
        assertSame(store.commands[0], store.commands[1]); assertEquals(1, store.prepares)
    }
}
