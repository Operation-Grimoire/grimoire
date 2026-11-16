package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReaderCheckpointsTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val pages = listOf("First", "Second", "Third").mapIndexed { i, s -> NovelPage(i, PageContent.Text(s)) }
    private val observation = ChapterStorageObservation(key, "scope", "generation", ChapterContentPresence.SAVED)
    private fun projection(body: List<NovelPage> = pages) = ReadingProjection.create(key, body,
        body.mapIndexed { i, p -> ProjectedPage(i, p) })
    private fun anchor(p: ReadingProjection, index: Int = 1) = requireNotNull(p.capture(index))

    private inner class Dao : ReadingCheckpointDao {
        var state: ReadingCheckpointState? = null
        var current = true
        var serial = 0
        var entered: CompletableDeferred<Unit>? = null
        var release: CompletableDeferred<Unit>? = null
        var faultBefore = false
        var faultAfter = false
        var reads = 0
        var registering: CompletableDeferred<Unit>? = null
        var registerRelease: CompletableDeferred<Unit>? = null
        val committed = mutableListOf<CheckpointCommit.Committed>()
        override suspend fun register(observation: ChapterStorageObservation, document: CanonicalDocument): ReadingSession? {
            if (!current || !canRegisterReadingSession(observation, document)) return null
            return ReadingSession("session${++serial}", observation, document, state?.anchor).also {
                state = ReadingCheckpointState(it.binding, anchor = state?.anchor)
                registering?.complete(Unit); registerRelease?.await()
            }
        }
        override suspend fun read(key: ChapterKey): ReadingAnchor? { reads++; return state?.anchor }
        override suspend fun commit(command: CheckpointCommand): CheckpointCommit {
            entered?.complete(Unit); release?.await()
            if (faultBefore) throw IOException("Before commit")
            return decideCheckpoint(command, state, current).also { result ->
                if (result is CheckpointCommit.Committed) {
                    state = ReadingCheckpointState(command.session.binding, command.sequence, command.anchor)
                    committed += result
                }
                if (faultAfter) throw IOException("Lost reply")
            }
        }
    }

    @Test fun openingUsesAtomicallyRegisteredSnapshotAndNeverCapturesRestoration() = runBlocking<Unit> {
        val dao = Dao(); val first = requireNotNull(dao.register(observation, projection().document!!))
        dao.commit(CheckpointCommand(first, 1, anchor(projection())))
        val coordinator = ReaderCheckpoints(dao)
        val load = coordinator.open(projection(), observation, LegacyReaderViewport(2, 99))
        assertEquals(1, load.restore.viewport.index); assertEquals(0, load.restore.viewport.offset)
        assertTrue(load.restore.logical is AnchorResolution.Exact)
        assertFalse(coordinator.canRecordPosition(load))
        assertEquals(0, dao.reads) // Not a racy read before/after registration.
        assertNull(coordinator.capture(load, 0)); coordinator.flush()
        assertEquals(1, dao.committed.size)
        coordinator.userPosition(load); assertTrue(coordinator.canRecordPosition(load))
        assertEquals(anchor(load.projection, 2), coordinator.capture(load, 2))
        coordinator.flush(); assertEquals(2, dao.committed.size)
    }

    @Test fun olderSuccessfulReplyCannotClearANewerBackwardCapture() = runBlocking<Unit> {
        val dao = Dao(); val coordinator = ReaderCheckpoints(dao); val load = coordinator.open(projection(), observation, null)
        coordinator.userPosition(load); coordinator.capture(load, 2)
        dao.entered = CompletableDeferred(); dao.release = CompletableDeferred()
        val writing = async { coordinator.flush() }; dao.entered!!.await()
        coordinator.capture(load, 0)
        dao.release!!.complete(Unit); writing.await()
        assertEquals(2, (dao.state!!.anchor!!.position as AnchorPosition.Block).ordinal)
        dao.entered = null; dao.release = null; coordinator.flush()
        assertEquals(listOf(1L, 2L), dao.committed.map { it.sequence })
        assertEquals(0, (dao.state!!.anchor!!.position as AnchorPosition.Block).ordinal)
    }

    @Test fun failedFlushRetainsNewestCaptureAndLostReplyRetriesIdentically() = runBlocking<Unit> {
        val dao = Dao(); val coordinator = ReaderCheckpoints(dao); val load = coordinator.open(projection(), observation, null)
        coordinator.userPosition(load); coordinator.capture(load, 1)
        dao.faultBefore = true
        try { coordinator.flush(); fail("Fault ignored") } catch (e: IOException) { assertEquals("Before commit", e.message) }
        coordinator.capture(load, 2); dao.faultBefore = false; dao.faultAfter = true
        try { coordinator.flush(); fail("Lost reply ignored") } catch (e: IOException) { assertEquals("Lost reply", e.message) }
        dao.faultAfter = false; coordinator.flush()
        assertEquals(listOf(2L, 2L), dao.committed.map { it.sequence })
        assertFalse(dao.committed.first().replayed); assertTrue(dao.committed.last().replayed)
        coordinator.flush(); assertEquals(2, dao.committed.size)
    }

    @Test fun cancellationAfterCommitRetainsTheUnconfirmedCommandForReplay() = runBlocking<Unit> {
        val underlying = Dao()
        val dao = object : ReadingCheckpointDao by underlying {
            var cancel = true
            override suspend fun commit(command: CheckpointCommand): CheckpointCommit {
                val result = underlying.commit(command)
                if (cancel) currentCoroutineContext().cancel()
                return result
            }
        }
        val coordinator = ReaderCheckpoints(dao); val load = coordinator.open(projection(), observation, null)
        coordinator.userPosition(load); coordinator.capture(load, 1)
        val child = launch { coordinator.flush() }; child.join(); assertTrue(child.isCancelled)
        dao.cancel = false; coordinator.flush()
        assertTrue(underlying.committed.last().replayed)
    }

    @Test fun retirementRejectsLateSamplesButDrainsEarlierCapturesBeforeSameChapterReopen() = runBlocking<Unit> {
        val dao = Dao(); val coordinator = ReaderCheckpoints(dao)
        val first = coordinator.open(projection(), observation, null)
        coordinator.userPosition(first); coordinator.capture(first, 1); coordinator.retire()
        assertNull(coordinator.capture(first, 2)); assertFalse(coordinator.canObserve(first))
        val second = coordinator.open(projection(), observation, null)
        assertEquals(1, second.restore.viewport.index); assertEquals(1, dao.committed.size)
        coordinator.userPosition(first); assertNull(coordinator.capture(first, 0))
        coordinator.userPosition(second); assertNotNull(coordinator.capture(second, 2)); coordinator.flush()
        assertEquals(2, (dao.state!!.anchor!!.position as AnchorPosition.Block).ordinal)
    }

    @Test fun retiredOpeningCannotPublishAfterANewerLoadHasStarted() = runBlocking<Unit> {
        val dao = Dao(); val coordinator = ReaderCheckpoints(dao)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        dao.registering = entered; dao.registerRelease = release
        val old = async { coordinator.open(projection(), observation, null) }; entered.await()
        coordinator.retire()
        dao.registering = null; dao.registerRelease = null
        val latest = async(start = CoroutineStart.UNDISPATCHED) { coordinator.open(projection(), observation, null) }
        release.complete(Unit)
        try { old.await(); fail("Retired preparation published") } catch (_: CancellationException) { }
        val load = latest.await(); assertTrue(coordinator.canObserve(load))
        coordinator.userPosition(load); assertNotNull(coordinator.capture(load, 1)); coordinator.flush()
        assertEquals(1, dao.committed.size)
    }

    @Test fun staleSessionIsRetiredWithoutBlockingFreshReloadOrRetryingItsNewerSamples() = runBlocking<Unit> {
        val dao = Dao(); val coordinator = ReaderCheckpoints(dao); val old = coordinator.open(projection(), observation, null)
        coordinator.userPosition(old); coordinator.capture(old, 1); dao.current = false
        coordinator.flush(); assertTrue(coordinator.isRevoked(old)); assertNull(coordinator.capture(old, 2))
        dao.current = true
        val fresh = coordinator.open(projection(), observation.copy(generation = "fresh"), null)
        coordinator.userPosition(fresh); assertNotNull(coordinator.capture(fresh, 2)); coordinator.flush()
        assertEquals(1, dao.committed.size)
    }

    @Test fun unresolvedFallbackPreservesOldAnchorUntilActualInputAndLegacyIsOnlyApproximate() = runBlocking<Unit> {
        val dao = Dao(); val original = projection(); val initial = requireNotNull(dao.register(observation, original.document!!))
        val oldAnchor = anchor(original)
        dao.commit(CheckpointCommand(initial, 1, oldAnchor))
        val coordinator = ReaderCheckpoints(dao)
        val changed = projection(listOf(NovelPage(0, PageContent.Text("Replacement"))))
        val load = coordinator.open(changed, observation, LegacyReaderViewport(2, 99))
        assertEquals(CheckpointNotice.CHANGED, load.restore.notice); assertEquals(0, load.restore.viewport.index)
        assertFalse(coordinator.canRecordPosition(load)); assertNull(coordinator.capture(load, 0)); coordinator.flush()
        assertEquals(oldAnchor, dao.state!!.anchor)
        coordinator.userPosition(load); assertTrue(coordinator.canRecordPosition(load)); coordinator.capture(load, 0); coordinator.flush()
        assertEquals(changed.document!!.logicalRevision, dao.state!!.anchor!!.logicalRevision)
        val legacy = restoreReaderCheckpoint(original, null, LegacyReaderViewport(2, 99))
        assertEquals(CheckpointNotice.LEGACY, legacy.notice); assertEquals(ReaderViewport(2, 99), legacy.viewport)
    }

    @Test fun restorationReportsRelocationHiddenPrecisionErrorsAndEndWithoutRewritingEvidence() {
        val original = projection(); val old = anchor(original)
        val inserted = projection(listOf(NovelPage(0, PageContent.Text("Opening"))) + pages)
        val moved = restoreReaderCheckpoint(inserted, old, null)
        assertEquals(CheckpointNotice.RELOCATED, moved.notice); assertEquals(2, moved.viewport.index)
        val hidden = ReadingProjection.create(key, pages, listOf(ProjectedPage(2, pages[2])))
        assertEquals(CheckpointNotice.HIDDEN, restoreReaderCheckpoint(hidden, old, null).notice)
        val precise = old.copy(position = (old.position as AnchorPosition.Block).copy(textOffset = 1, precision = AnchorPrecision.TEXT_OFFSET))
        assertEquals(CheckpointNotice.COARSE, restoreReaderCheckpoint(original, precise, null).notice)
        assertEquals(CheckpointNotice.UNSUPPORTED, restoreReaderCheckpoint(original, old.copy(modelVersion = 99), null).notice)
        assertEquals(CheckpointNotice.INVALID, restoreReaderCheckpoint(original, old.copy(logicalRevision = "bad"), null).notice)
        val end = old.copy(position = AnchorPosition.End)
        assertTrue(restoreReaderCheckpoint(original, end, null).viewport.end)
        assertEquals(1, (old.position as AnchorPosition.Block).ordinal)
    }
}
