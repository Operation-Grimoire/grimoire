package io.grimoire.reading.storage

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DownloadRepositoryTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val attempt = DownloadAttempt(key, "attempt-A", "revision-0")
    private val pages = listOf(NovelPage(0, PageContent.Text("one two\u001fthree", "<b>four</b>")),
        NovelPage(1, PageContent.Image("https://example.invalid/five")))

    private class Backend : DownloadDao {
        var claim: suspend (ChapterKey) -> DownloadAttempt? = { null }
        var commit: suspend (DownloadAttempt, PreparedDownload) -> DownloadCommit = { _, _ -> error("Unexpected commit") }
        var fail: suspend (DownloadAttempt, DownloadFailure) -> Boolean = { _, _ -> false }
        var receipt: DownloadReceipt? = null
        override suspend fun claim(key: ChapterKey) = claim.invoke(key)
        override suspend fun commit(attempt: DownloadAttempt, prepared: PreparedDownload) = commit.invoke(attempt, prepared)
        override suspend fun fail(attempt: DownloadAttempt, failure: DownloadFailure) = fail.invoke(attempt, failure)
        override suspend fun currentReceipt(key: ChapterKey) = receipt
    }

    private fun receipt(prepared: PreparedDownload, words: Int = prepared.wordCount) =
        DownloadReceipt(key, attempt.id, "revision-1", prepared.digest, prepared.content.version, words)

    @Test fun portableClaimRejectsForeignIdentityAndRetainsMissing() = runBlocking {
        val backend = Backend()
        val repository = DownloadRepository(backend)
        assertNull(repository.claim(key))
        backend.claim = { requested -> assertEquals(key, requested); attempt }
        assertEquals(attempt, repository.claim(key))
        backend.claim = { attempt.copy(key = key.copy(url = "/other")) }
        try { repository.claim(key); fail("Foreign claim accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun preparesOriginalVersionedPagesDigestAndProseCountOnce() = runBlocking {
        val backend = Backend()
        var calls = 0
        backend.commit = { claimed, prepared ->
            assertEquals(attempt, claimed)
            assertEquals(2, prepared.content.version)
            assertEquals(encodeChapterContent(pages), prepared.content)
            assertEquals(encodeChapterContent(pages), encodeChapterContent(decodeChapterContent(prepared.content)))
            assertEquals(3, prepared.wordCount)
            assertEquals(downloadDigest(prepared.content), prepared.digest)
            calls++
            DownloadCommit.Committed(receipt(prepared), replayed = false)
        }
        assertFalse((DownloadRepository(backend).commit(attempt, pages) as DownloadCommit.Committed).replayed)
        assertEquals(1, calls)
    }

    @Test fun zeroWordCountRetainsBackendCountAndReplayIsExplicit() = runBlocking {
        val backend = Backend()
        backend.commit = { _, prepared -> DownloadCommit.Committed(receipt(prepared, 14), replayed = true) }
        val result = DownloadRepository(backend).commit(attempt, emptyList()) as DownloadCommit.Committed
        assertEquals(14, result.receipt.wordCount)
        assertTrue(result.replayed)
    }

    @Test fun staleOutcomePropagatesWithoutRetryOrCompensatingMutation() = runBlocking {
        val backend = Backend()
        var calls = 0
        backend.commit = { _, _ -> calls++; DownloadCommit.NotApplied(DownloadRejection.STALE) }
        assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), DownloadRepository(backend).commit(attempt, pages))
        assertEquals(1, calls)
    }

    @Test fun rejectsWrongOrUnconfirmedReceiptFields() = runBlocking {
        val backend = Backend()
        for (change in listOf<(DownloadReceipt) -> DownloadReceipt>(
            { it.copy(key = key.copy(url = "/foreign")) }, { it.copy(attemptId = "another") },
            { it.copy(revision = "") }, { it.copy(digest = "0".repeat(64)) },
            { it.copy(contentVersion = 99) }, { it.copy(wordCount = -1) }, { it.copy(wordCount = 999) },
        )) {
            backend.commit = { _, prepared -> DownloadCommit.Committed(change(receipt(prepared)), false) }
            try { DownloadRepository(backend).commit(attempt, pages); fail("Invalid receipt accepted") }
            catch (_: IllegalArgumentException) { }
        }
        backend.receipt = DownloadReceipt(key.copy(url = "/foreign"), "A", "R", "0".repeat(64), 2, 1)
        try { DownloadRepository(backend).currentReceipt(key); fail("Foreign lookup accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun storageAndCancellationErrorsPropagateWithoutRetry() = runBlocking {
        val backend = Backend()
        for (failure in listOf(IOException("disk"), SecurityException("denied"), CancellationException("cancelled"))) {
            var calls = 0
            backend.commit = { _, _ -> calls++; throw failure }
            try { DownloadRepository(backend).commit(attempt, pages); fail("Failure hidden") }
            catch (actual: Exception) { assertSame(failure, actual) }
            assertEquals(1, calls)
        }
    }

    @Test fun failureCallbacksRetainOwnershipAndKind() = runBlocking {
        val backend = Backend()
        backend.fail = { claimed, kind -> assertEquals(attempt, claimed); kind == DownloadFailure.CONNECTIVITY }
        val repository = DownloadRepository(backend)
        assertTrue(repository.fail(attempt, DownloadFailure.CONNECTIVITY))
        assertFalse(repository.fail(attempt, DownloadFailure.ERROR))
    }

    @Test fun cancellationBeforeEntryDoesNotCallBackend() = runBlocking {
        val backend = Backend()
        backend.claim = { error("Cancelled claim") }
        val repository = DownloadRepository(backend)
        var outcome: Result<DownloadAttempt?>? = null
        val child = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            outcome = runCatching { repository.claim(key) }
        }
        child.join()
        assertTrue(outcome?.exceptionOrNull() is CancellationException)
    }

    @Test fun cancelledConfirmationCanBeReconciledFromCommittedReceipt() = runBlocking {
        val backend = Backend()
        backend.commit = { _, prepared ->
            backend.receipt = receipt(prepared)
            currentCoroutineContext().cancel()
            DownloadCommit.Committed(backend.receipt!!, false)
        }
        val repository = DownloadRepository(backend)
        var outcome: Result<DownloadCommit>? = null
        val child = launch(start = CoroutineStart.UNDISPATCHED) { outcome = runCatching { repository.commit(attempt, pages) } }
        child.join()
        assertTrue(outcome?.exceptionOrNull() is CancellationException)
        assertEquals(backend.receipt, repository.currentReceipt(key))
    }
}
