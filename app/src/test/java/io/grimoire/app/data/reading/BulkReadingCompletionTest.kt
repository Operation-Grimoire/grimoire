package io.grimoire.app.data.reading

import io.grimoire.reading.state.NovelReadingCompletionCommit
import io.grimoire.reading.state.NovelReadingCompletionDao
import io.grimoire.reading.state.NovelReadingCompletionFacade
import io.grimoire.reading.state.NovelReadingCompletionIntent
import io.grimoire.reading.state.PreparedNovelReadingCompletion
import io.grimoire.reading.state.PreparedReadingCompletionBatch
import io.grimoire.reading.state.ReadingCompletionSelection
import io.grimoire.reading.state.SelectedReadingCompletionIntent
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BulkReadingCompletionTest {
    private class Batch(val id: Int, override val read: Boolean) : PreparedReadingCompletionBatch

    private class Store : NovelReadingCompletionDao {
        var prepared: SelectedReadingCompletionIntent? = null
        val attempts = mutableListOf<Int>()
        val committed = mutableSetOf<Int>()
        var loseReplyFor: Int? = null

        override suspend fun prepareNovelCompletion(intent: NovelReadingCompletionIntent): PreparedNovelReadingCompletion? =
            error("Expected a selection")

        override suspend fun prepareSelectedCompletion(intent: SelectedReadingCompletionIntent): List<PreparedReadingCompletionBatch> {
            check(prepared == null)
            prepared = intent
            return listOf(Batch(1, intent.read), Batch(2, intent.read))
        }

        override suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit =
            error("Expected a selection")

        override suspend fun commitSelectedCompletion(command: PreparedReadingCompletionBatch): NovelReadingCompletionCommit {
            val id = (command as Batch).id
            attempts += id
            val replayed = !committed.add(id)
            if (loseReplyFor == id) {
                loseReplyFor = null
                throw IOException("Lost reply")
            }
            return NovelReadingCompletionCommit.Committed(replayed)
        }
    }

    @Test fun retriesOnlyUncertainGroupWithCapturedSelection() = runBlocking<Unit> {
        val store = Store().apply { loseReplyFor = 2 }
        val completion = BulkReadingCompletion(this, NovelReadingCompletionFacade(store, null))
        val ids = mutableListOf(13L, 17L)
        completion.markNovels(ids, read = false)
        ids.add(99)
        yield()
        assertEquals(ReadingCompletionSelection.Novels(listOf(13, 17)), store.prepared?.selection)
        assertEquals(CompletionSaveNotice.PARTIAL_SAVE_FAILED, completion.notice.value)
        assertEquals(listOf(1, 2), store.attempts)

        completion.retry()
        yield()
        assertEquals(listOf(1, 2, 2), store.attempts)
        assertEquals(setOf(1, 2), store.committed)
        assertNull(completion.notice.value)
    }

    @Test fun retriesBackfillThenResumesWithNextGroup() = runBlocking<Unit> {
        val store = Store()
        var backfills = 0
        val facade = NovelReadingCompletionFacade(store) {
            if (++backfills == 1) throw IOException("Backfill failed")
        }
        val completion = BulkReadingCompletion(this, facade)
        completion.markChapters(listOf(11, 12), read = true, novelId = 5, chunkSize = 1)
        yield()
        assertEquals(listOf(1), store.attempts)
        assertEquals(CompletionSaveNotice.PARTIAL_WORD_COUNT_FAILED, completion.notice.value)

        completion.retry()
        yield()
        assertEquals(listOf(1, 2), store.attempts)
        assertEquals(3, backfills)
        assertNull(completion.notice.value)
    }
}
