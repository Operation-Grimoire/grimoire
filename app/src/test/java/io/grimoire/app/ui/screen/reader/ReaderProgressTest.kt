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

    @Test fun readTriggersKeepTheirBoundariesAndDoNotUnmarkReadChapters() {
        fun mark(strategy: MarkAsReadStrategy, fraction: Float = 0f, last: Int = 5, total: Int = 10, read: Boolean = false) =
            shouldMarkReaderChapterRead(read, fraction, last, total, strategy, 95, 3)
        assertFalse(mark(MarkAsReadStrategy.PERCENT, fraction = 0.94f))
        assertTrue(mark(MarkAsReadStrategy.PERCENT, fraction = 0.95f))
        assertFalse(mark(MarkAsReadStrategy.PARAGRAPHS_FROM_END, last = 5))
        assertTrue(mark(MarkAsReadStrategy.PARAGRAPHS_FROM_END, last = 6))
        assertFalse(mark(MarkAsReadStrategy.AT_END, last = 8))
        assertTrue(mark(MarkAsReadStrategy.AT_END, last = 9))
        assertFalse(mark(MarkAsReadStrategy.AT_END, last = 9, read = true))
        assertFalse(mark(MarkAsReadStrategy.AT_END, total = 0))
    }
}
