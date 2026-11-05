package io.grimoire.app.data.download

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.util.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.SocketException
import java.net.ProtocolException
import java.net.UnknownHostException
import java.io.EOFException
import java.io.FileNotFoundException
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ChapterContentValidationTest {

    @Test
    fun hasReadableContent_emptyList_isFalse() {
        assertFalse(hasReadableContent(emptyList()))
    }

    @Test
    fun hasReadableContent_blankAndSeparatorOnly_isFalse() {
        val pages = listOf(
            NovelPage(0, PageContent.Text("   ")),
            NovelPage(1, PageContent.Separator()),
            NovelPage(2, PageContent.Text("\n\t")),
        )
        assertFalse(hasReadableContent(pages))
    }

    @Test
    fun hasReadableContent_anyProse_isTrue() {
        val pages = listOf(
            NovelPage(0, PageContent.Text("  ")),
            NovelPage(1, PageContent.Text("Real prose here.")),
        )
        assertTrue(hasReadableContent(pages))
    }

    @Test
    fun hasReadableContent_imageOnly_isTrue() {
        val pages = listOf(NovelPage(0, PageContent.Image("https://x/i.png")))
        assertTrue(hasReadableContent(pages))
    }

    @Test
    fun hasReadableContent_formattedOnly_isTrue() {
        val pages = listOf(NovelPage(0, PageContent.Text("", "<b>hi</b>")))
        assertTrue(hasReadableContent(pages))
    }

    @Test
    fun fetchReadablePages_returnsFirstNonEmptyResult() = runBlocking {
        val attempts = AtomicInteger(0)
        val pages = fetchReadablePages(sleep = {}) {
            attempts.incrementAndGet()
            listOf(NovelPage(0, PageContent.Text("content")))
        }
        assertEquals(1, attempts.get())
        assertEquals("content", pages.single().text)
    }

    @Test
    fun fetchReadablePages_retriesEmptyThenSucceeds() = runBlocking {
        val attempts = AtomicInteger(0)
        val slept = mutableListOf<Long>()
        val pages = fetchReadablePages(sleep = { slept += it }) {
            if (attempts.incrementAndGet() < 3) emptyList()
            else listOf(NovelPage(0, PageContent.Text("finally")))
        }
        assertEquals(3, attempts.get())
        // Backed off between the two empty attempts.
        assertEquals(listOf(1_000L, 2_000L), slept)
        assertEquals("finally", pages.single().text)
    }

    @Test(expected = EmptyChapterContentException::class)
    fun fetchReadablePages_alwaysEmpty_throws() = runBlocking {
        fetchReadablePages(sleep = {}) { emptyList<NovelPage>() }
        Unit
    }

    @Test
    fun transientSocketFailureRetriesThenReturnsContent() = runBlocking {
        var attempts = 0
        val slept = mutableListOf<Long>()
        val pages = fetchReadablePages(sleep = { slept += it }) {
            if (++attempts < 3) throw SocketTimeoutException("stalled")
            listOf(NovelPage(0, PageContent.Text("recovered")))
        }
        assertEquals("recovered", pages.single().text)
        assertEquals(listOf(1_000L, 2_000L), slept)
    }

    @Test
    fun exhaustedConnectivityRetriesPreserveFailureForQueueRecovery() = runBlocking {
        val failure = SocketException("offline")
        var attempts = 0
        try {
            fetchReadablePages(sleep = {}) { attempts++; throw failure }
            throw AssertionError("expected exception")
        } catch (e: IOException) {
            assertSame(failure, e)
        }
        assertEquals(3, attempts)
    }

    @Test
    fun cancellationNeverRetries() = runBlocking {
        var attempts = 0
        try {
            fetchReadablePages(sleep = { throw AssertionError("must not sleep") }) {
                attempts++
                throw CancellationException("stopped")
            }
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }

    @Test
    fun connectivityClassificationIncludesWrappedIoButExcludesParserErrors() {
        assertTrue(IllegalStateException("source failed", UnknownHostException("offline")).isConnectivityFailure())
        assertFalse(IllegalArgumentException("bad markup").isConnectivityFailure())
        assertTrue(SocketTimeoutException("stalled").isConnectivityFailure())
        assertTrue(EOFException("truncated response").isConnectivityFailure())
        assertFalse(ProtocolException("permanent source response").isConnectivityFailure())
        assertFalse(FileNotFoundException("local file missing").isConnectivityFailure())
        assertFalse(IOException("source rejected the request").isConnectivityFailure())
    }

    @Test
    fun fetchReadablePages_propagatesFetchException() = runBlocking {
        val attempts = AtomicInteger(0)
        try {
            fetchReadablePages(sleep = {}) {
                attempts.incrementAndGet()
                throw IllegalStateException("boom")
            }
            throw AssertionError("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        // Hard failures are not retried — surfaced on the first attempt.
        assertEquals(1, attempts.get())
    }
}
