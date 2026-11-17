package io.grimoire.reading

import io.grimoire.api.model.novel.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReadingStateFacadeTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val pages = listOf(NovelPage(0, PageContent.Text("Body")))
    private val projection = ReadingProjection.create(key, pages, listOf(ProjectedPage(0, pages[0])))
    private val document = projection.document!!
    private val content = ChapterStorageObservation(key, "content", "generation", ChapterContentPresence.ABSENT)
    private inner class Store : ReadingStateDao {
        var record = ReadingStateRecord("incarnation", 0, ReadingCompletionState(key, ReadStatus.UNREAD, null))
        val receipts = mutableMapOf<String, ReadingStateReceipt>()
        var prepares = 0; var missing = false; var lostReply = false; var failBefore = false
        override suspend fun prepare(key: ChapterKey): PreparedReadingState? {
            prepares++
            return if (missing || key != this@ReadingStateFacadeTest.key) null else PreparedReadingState(
                ReadingStateBinding(key, "owner", record.incarnation, record.intentRevision), record.value)
        }
        override suspend fun register(binding: ReadingStateBinding, content: ChapterStorageObservation,
            document: CanonicalDocument, kind: ReadingStateWriterKind): ReadingStateSession =
            ReadingStateSession("writer-$kind", binding, content, document, kind).also { record = bindReadingStateWriter(record, it) }
        override suspend fun retire(session: ReadingStateSession): Boolean {
            val next = retireReadingStateWriter(record, session, session.binding.scope == "owner") ?: return false
            record = next; return true
        }
        override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit {
            if (failBefore) throw IOException("Before commit")
            val next = decideReadingState(command, record, receipts[command.id], command.binding.scope == "owner", true)
            next.record?.let { record = it; receipts[command.id] = next.receipt!! }
            if (lostReply) { lostReply = false; throw IOException("Lost reply") }
            return next.result
        }
        override suspend fun read(key: ChapterKey): ReadingCompletionState? = if (missing) null else record.value
    }
    private suspend fun session(store: Store, kind: ReadingStateWriterKind) = store.register(store.prepare(key)!!.binding, content, document, kind)
    private fun audio(s: ReadingStateSession) = ReadingCompletionInput.Audio(key,
        AudioCompletionEvidence(s.id, content, document.logicalRevision, 1, 0, true),
        AudioCompletionContext(s.id, content, document, 1, true, true), 100)

    @Test fun lostReplyRetriesTheSameIdentityTimestampAndPreparedRevision() = runBlocking<Unit> {
        val store = Store(); var effects = 0; val facade = ReadingStateFacade(store) { effects++ }
        val command = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100), "read")!!
        store.lostReply = true
        try { facade.submit(command); fail("Lost reply") } catch (e: IOException) { assertEquals("Lost reply", e.message) }
        assertEquals(0, effects); assertEquals(1L, store.record.intentRevision)
        val result = facade.submit(command)
        assertEquals(ReadingStateConfirmation.Confirmed(store.record.value, true), result.confirmation)
        assertEquals(100L, result.current!!.firstReadAt); assertEquals(1, store.prepares); assertEquals(1, store.receipts.size)
        assertEquals(ReadingWordCountOutcome.Completed, result.wordCount); assertEquals(1, effects)
    }

    @Test fun backfillFailureIsPartialAndRetryCannotOverwriteLaterUnread() = runBlocking<Unit> {
        val store = Store(); var broken = true; var effects = 0
        val facade = ReadingStateFacade(store) { effects++; if (broken) throw IOException("Backfill") }
        val read = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100), "read")!!
        val partial = facade.submit(read)
        assertTrue(partial.confirmation is ReadingStateConfirmation.Confirmed)
        assertEquals("Backfill", (partial.wordCount as ReadingWordCountOutcome.Failed).cause.message)
        val unread = facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key), "unread")!!
        facade.submit(unread); broken = false
        val retry = facade.submit(read)
        assertEquals(ReadStatus.READ, (retry.confirmation as ReadingStateConfirmation.Confirmed).valueAtCommit.status)
        assertTrue(retry.confirmation.replayed); assertEquals(ReadStatus.UNREAD, retry.current!!.status)
        assertEquals(100L, retry.current.firstReadAt); assertEquals(2L, store.record.intentRevision); assertEquals(2, effects)
    }

    @Test fun reconcileAfterSuspendedBackfillUsesNewerIntentNotTheCommitSnapshot() = runBlocking<Unit> {
        val store = Store(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val facade = ReadingStateFacade(store) { entered.complete(Unit); release.await() }
        val read = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100), "read")!!
        val pending = async { facade.submit(read) }; entered.await()
        val other = ReadingStateFacade(store, null)
        other.submit(other.prepareExplicit(ReadingCompletionInput.MarkUnread(key), "unread")!!)
        release.complete(Unit)
        assertEquals(ReadStatus.UNREAD, pending.await().current!!.status)
        assertEquals(2L, store.record.intentRevision)
    }

    @Test fun cancellationAfterCommitRetainsAnOperationThatCanBeConfirmedExactly() = runBlocking<Unit> {
        val store = Store(); val entered = CompletableDeferred<Unit>(); var block = true
        val facade = ReadingStateFacade(store) { if (block) { entered.complete(Unit); awaitCancellation() } }
        val command = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100), "read")!!
        val pending = launch { facade.submit(command) }; entered.await(); pending.cancelAndJoin(); block = false
        assertTrue(pending.isCancelled); assertEquals(1L, store.record.intentRevision)
        val retry = facade.submit(command)
        assertTrue((retry.confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertEquals(ReadingWordCountOutcome.Completed, retry.wordCount); assertEquals(1L, store.record.intentRevision)
    }

    @Test fun rejectedConflictingIntentIsNeverRepreparedOrBackfilled() = runBlocking<Unit> {
        val store = Store(); var effects = 0; val facade = ReadingStateFacade(store) { effects++ }
        val read = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100), "read")!!
        val conflict = facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key), "conflict")!!
        facade.submit(read); val result = facade.submit(conflict)
        assertEquals(ReadingStateConfirmation.NotApplied(ReadingStateRejection.CONFLICT), result.confirmation)
        assertEquals(ReadingWordCountOutcome.NotRequested, result.wordCount)
        assertEquals(2, store.prepares); assertEquals(1, effects); assertEquals(ReadStatus.READ, result.current!!.status)
    }

    @Test fun audioNoChangeStillReportsLegacyBackfillWithoutReservingReceipts() = runBlocking<Unit> {
        val store = Store(); var effects = 0; val facade = ReadingStateFacade(store) { effects++ }
        facade.submit(facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100))!!)
        val s = session(store, ReadingStateWriterKind.AUDIO)
        val input = audio(s); val command = facade.prepareAutomatic(s, input, 1, "audio")
        val result = facade.submit(command)
        assertSame(input, command.input); assertSame(s, command.session)
        assertEquals(ReadingStateConfirmation.NotApplied(ReadingStateRejection.NO_CHANGE), result.confirmation)
        assertEquals(ReadingWordCountOutcome.Completed, result.wordCount); assertEquals(2, effects)
        assertEquals(1, store.receipts.size); assertEquals(0L, store.record.audio!!.sequence)
    }

    @Test fun disabledVisualAndIneligibleAudioNeverRunBackfill() = runBlocking<Unit> {
        val store = Store(); var effects = 0; val facade = ReadingStateFacade(store) { effects++ }
        val visual = session(store, ReadingStateWriterKind.VISUAL)
        val input = ReadingCompletionInput.Visual(key,
            VisualCompletionEvidence(visual.id, content, document.logicalRevision, AndroidViewportV1.create(0, 0, 1, 1f)!!),
            VisualCompletionContext(visual.id, content, projection, true, true), AutomaticReadPolicy.Disabled, 100)
        assertEquals(ReadingStateConfirmation.NotApplied(ReadingStateRejection.NO_CHANGE), facade.submit(facade.prepareAutomatic(visual, input, 1)).confirmation)
        val s = session(store, ReadingStateWriterKind.AUDIO); val bad = audio(s).copy(current = audio(s).current.copy(playing = false))
        assertEquals(ReadingStateConfirmation.NotApplied(ReadingStateRejection.INELIGIBLE), facade.submit(facade.prepareAutomatic(s, bad, 1)).confirmation)
        assertEquals(0, effects); assertTrue(store.receipts.isEmpty())
        for ((wrongSession, wrongInput) in listOf(visual to audio(s), s to input)) {
            try { facade.prepareAutomatic(wrongSession, wrongInput, 1); fail("Kind mismatch must fail at preparation") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun missingTargetAndNoLegacyEffectStayExplicit() = runBlocking<Unit> {
        val store = Store(); val facade = ReadingStateFacade(store, null)
        store.missing = true; assertNull(facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100)))
        store.missing = false
        val command = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100))!!
        assertEquals(ReadingWordCountOutcome.NotApplicable, facade.submit(command).wordCount)
        store.missing = true; assertNull(facade.submit(command).current)
    }

    @Test fun faultBeforeCommitDoesNotRunSideEffectAndRetryAppliesOnce() = runBlocking<Unit> {
        val store = Store(); var effects = 0; val facade = ReadingStateFacade(store) { effects++ }
        val command = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100))!!
        store.failBefore = true
        try { facade.submit(command); fail("Write fault") } catch (_: IOException) { }
        assertEquals(0, effects); assertEquals(0L, store.record.intentRevision)
        store.failBefore = false; val result = facade.submit(command)
        assertFalse((result.confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertEquals(1, effects); assertEquals(1, store.prepares)
    }

    @Test fun exactRetirementPreservesOtherWriterValueRevisionAndKnownReceipts() = runBlocking<Unit> {
        val store = Store(); val visual = session(store, ReadingStateWriterKind.VISUAL); val audio = session(store, ReadingStateWriterKind.AUDIO)
        val facade = ReadingStateFacade(store, null); val command = facade.prepareAutomatic(visual,
            ReadingCompletionInput.Visual(key, VisualCompletionEvidence(visual.id, content, document.logicalRevision, AndroidViewportV1.create(0, 0, 1, 1f)!!),
                VisualCompletionContext(visual.id, content, projection, true, true), AutomaticReadPolicy.AtEnd, 100), 1)
        facade.submit(command); val before = store.record; val receipts = store.receipts.toMap()
        assertTrue(store.retire(visual)); assertFalse(store.retire(visual))
        assertEquals(before.copy(visual = null), store.record); assertEquals(receipts, store.receipts)
        assertTrue((facade.submit(command).confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertNotNull(store.record.audio); assertTrue(store.retire(audio)); assertNull(store.record.audio)
    }

    @Test fun retirementRefusesOldScopeIdentityIntentAndSupersededOrForgedHandles() = runBlocking<Unit> {
        val store = Store(); val s = session(store, ReadingStateWriterKind.VISUAL); val before = store.record
        val mutations = listOf(s.copy(binding = s.binding.copy(scope = "old")), s.copy(binding = s.binding.copy(incarnation = "old")),
            s.copy(binding = s.binding.copy(intentRevision = 1)), s.copy(binding = s.binding.copy(key = key.copy(url = "/other"))),
            s.copy(content = content.copy(generation = "old")), s.copy(id = "old"), s.copy(document = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text("Different"))))))
        for (bad in mutations) { assertFalse(store.retire(bad)); assertEquals(before, store.record) }
        val newer = store.register(s.binding, content, document, s.kind)
        // Give replacement its own identity: fake register otherwise deliberately uses deterministic IDs.
        val replacement = newer.copy(id = "replacement"); store.record = bindReadingStateWriter(store.record, replacement)
        assertFalse(store.retire(s)); assertTrue(store.retire(replacement))
    }
}
