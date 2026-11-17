package io.grimoire.app.ui.screen.reader

import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderProgressTest {
    @Test fun failedWriteRetainsNewestPositionForExplicitRetry() = runBlocking {
        val written = mutableListOf<ReaderPosition>(); val failures = mutableListOf<Exception>()
        var fail = true
        val writer = ReaderProgressWriter(this, onFailure = { failures += it }) {
            if (fail) throw java.io.IOException("Fixture failure")
            written += it
        }
        writer.submit(ReaderPosition(1, 0.1f, 1, 0)); writer.flush()!!.join()
        assertTrue(writer.hasPending()); assertEquals(1, failures.size); assertTrue(written.isEmpty())
        val newest = ReaderPosition(1, 0.2f, 2, 17)
        writer.submit(newest); fail = false; writer.flush()!!.join()
        assertEquals(listOf(newest), written); assertFalse(writer.hasPending())
    }

    @Test fun olderWriteCompletionCannotDropCaptureArrivingDuringItsSuspension() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val written = mutableListOf<ReaderPosition>()
        val writer = ReaderProgressWriter(this) {
            if (written.isEmpty()) { entered.complete(Unit); release.await() }
            written += it
        }
        val first = ReaderPosition(1, 0.7f, 7, 0); val backward = ReaderPosition(1, 0.1f, 1, 17)
        writer.submit(first); val job = writer.flush()!!; entered.await()
        writer.submit(backward); release.complete(Unit); job.join()
        assertTrue(writer.hasPending()); writer.flush()!!.join()
        assertEquals(listOf(first, backward), written); assertFalse(writer.hasPending())
    }

    @Test fun progressMustBelongToBothTheCurrentChapterAndItsPages() {
        assertTrue(acceptsReaderProgress(2, 2, 2))
        assertFalse(acceptsReaderProgress(2, 1, 2))
        assertFalse(acceptsReaderProgress(2, 2, 1))
        assertFalse(acceptsReaderProgress(2, null, 2))
        assertFalse(acceptsReaderProgress(null, 2, 2))
    }

    @Test fun livePositionPreservesReadStatusAndOtherChapterMetadata() {
        val chapter = ChapterEntity(id = 1, novelId = 5, url = "chapter", name = "One", read = true, wordCount = 250)
        val live = chapter.withReaderPosition(ReaderPosition(1, 0.4f, 8, 20))
        assertEquals(chapter.copy(readProgress = 0.4f, readAnchorItemIndex = 8, readAnchorItemOffset = 20), live)
        assertSame(chapter, chapter.withReaderPosition(ReaderPosition(2, 1f, 0, 0)))
    }

    @Test fun burstWritesOnlyItsLatestAnchorWhenFlushed() = runBlocking {
        val written = mutableListOf<ReaderPosition>()
        val writer = ReaderProgressWriter(this) { written += it }
        writer.submit(ReaderPosition(1, 0.1f, 1, 0))
        val latest = ReaderPosition(1, 0.2f, 2, 10)
        writer.submit(latest)
        delay(20)
        assertTrue(written.isEmpty())
        writer.flush()?.join()
        assertEquals(listOf(latest), written)
    }

    @Test fun quietPeriodPersistsWithoutAnExplicitFlush() = runBlocking {
        val written = CompletableDeferred<ReaderPosition>()
        val writer = ReaderProgressWriter(this, debounceMillis = 10) { written.complete(it) }
        val latest = ReaderPosition(1, 0.7f, 7, 30)
        writer.submit(latest)
        assertEquals(latest, withTimeout(5_000) { written.await() })
    }

    @Test fun chapterSwitchAndExitFlushInOrderAfterTheOwnerCloses() = runBlocking {
        val lifetime = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + lifetime)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val written = mutableListOf<Long>()
        val writer = ReaderProgressWriter(scope) {
            if (it.chapterId == 1L) { firstStarted.complete(Unit); releaseFirst.await() }
            written += it.chapterId
        }
        writer.submit(ReaderPosition(1, 0.5f, 5, 0))
        writer.submit(ReaderPosition(2, 0.2f, 2, 0))
        withTimeout(5_000) { firstStarted.await() }
        writer.flush()
        lifetime.complete()
        assertTrue(written.isEmpty())
        releaseFirst.complete(Unit)
        withTimeout(5_000) { lifetime.join() }
        assertEquals(listOf(1L, 2L), written)
    }

    @Test fun readerPreferencesMapToTheMatchingSharedPolicy() {
        assertEquals(io.grimoire.reading.state.AutomaticReadPolicy.Percent(95), readerAutomaticPolicy(MarkAsReadStrategy.PERCENT, 95, 3))
        assertEquals(io.grimoire.reading.state.AutomaticReadPolicy.ItemsFromEnd(3), readerAutomaticPolicy(MarkAsReadStrategy.PARAGRAPHS_FROM_END, 95, 3))
        assertEquals(io.grimoire.reading.state.AutomaticReadPolicy.AtEnd, readerAutomaticPolicy(MarkAsReadStrategy.AT_END, 95, 3))
    }
}
