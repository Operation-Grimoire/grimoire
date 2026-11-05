package io.grimoire.app.data.libraryupdate

import io.grimoire.api.network.CloudflareException
import io.grimoire.app.data.source.TruncatedChapterListException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SourceSyncPolicyTest {
    @Test(timeout = 5_000)
    fun `one source is serialized while another source can proceed`() = runBlocking {
        val gate = SourceSyncGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val first = launch {
            gate.run({ SourceSyncOutcome.SUCCESS }) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.run({ SourceSyncOutcome.SUCCESS }) { secondEntered.complete(Unit) }
        }
        assertFalse(secondEntered.isCompleted)
        assertEquals("other", SourceSyncGate().run({ SourceSyncOutcome.SUCCESS }) { "other" })
        release.complete(Unit)
        joinAll(first, second)
        assertTrue(secondEntered.isCompleted)
    }

    @Test
    fun `three consecutive failures stop further calls only for that run`() = runBlocking {
        val gate = SourceSyncGate()
        var calls = 0
        repeat(3) {
            gate.run({ SourceSyncOutcome.FAILURE }) { calls++; false }
        }
        assertNull(gate.run({ SourceSyncOutcome.SUCCESS }) { calls++; true })
        assertEquals(3, calls)
        assertEquals(true, SourceSyncGate().run({ SourceSyncOutcome.SUCCESS }) { true })
    }

    @Test
    fun `success resets failures but skipped novels do not`() = runBlocking {
        val gate = SourceSyncGate()
        repeat(2) { gate.run({ SourceSyncOutcome.FAILURE }) { false } }
        gate.run({ SourceSyncOutcome.SUCCESS }) { true }
        repeat(2) { gate.run({ SourceSyncOutcome.FAILURE }) { false } }
        gate.run({ SourceSyncOutcome.SKIPPED }) { false }
        assertEquals(false, gate.run({ SourceSyncOutcome.FAILURE }) { false })
        assertNull(gate.run({ SourceSyncOutcome.SUCCESS }) { true })
    }

    @Test
    fun `cancellation releases the source without counting a failure`() = runBlocking {
        val gate = SourceSyncGate(failureLimit = 1)
        try {
            gate.run<Unit>({ SourceSyncOutcome.FAILURE }) { throw CancellationException() }
        } catch (_: CancellationException) {
            // The next call must still be allowed.
        }
        assertEquals(true, gate.run({ SourceSyncOutcome.SUCCESS }) { true })
    }

    @Test
    fun `unexpected storage errors do not exhaust the source failure budget`() {
        val gate = SourceSyncGate(failureLimit = 1)
        assertThrows(IOException::class.java) {
            runBlocking {
                gate.run<Unit>({ SourceSyncOutcome.FAILURE }) { throw IOException("disk full") }
            }
        }
        runBlocking {
            assertEquals(true, gate.run({ SourceSyncOutcome.SUCCESS }) { true })
        }
    }

    @Test
    fun `permanent and interactive failures are not retried`() {
        val failures = listOf(
            IOException("HTTP 404 for https://source/book"),
            IllegalStateException("HTTP 403 for https://source/book"),
            IOException("request failed", IOException("HTTP 410")),
            IllegalArgumentException("Invalid source data"),
            CloudflareException("https://source/book"),
        )
        for (failure in failures) {
            var calls = 0
            val actual = assertThrows(Exception::class.java) {
                runBlocking { retrySourceCall(retryDelayMillis = 0) { calls++; throw failure } }
            }
            assertSame(failure, actual)
            assertEquals(1, calls)
        }
    }

    @Test
    fun `transient transport throttling and server failures can recover`() = runBlocking {
        val failures = listOf(
            IOException("connection reset"),
            IllegalStateException("HTTP 408"),
            IllegalStateException("HTTP 429"),
            IllegalStateException("HTTP 503"),
            TruncatedChapterListException(2),
        )
        for (failure in failures) {
            assertTrue(isRetryableSourceFailure(failure))
            var calls = 0
            val result = retrySourceCall(retryDelayMillis = 0) {
                if (++calls == 1) throw failure
                "recovered"
            }
            assertEquals("recovered", result)
            assertEquals(2, calls)
        }
    }

    @Test
    fun `retries stop after three attempts and never retry cancellation`() {
        var calls = 0
        assertThrows(IOException::class.java) {
            runBlocking { retrySourceCall(retryDelayMillis = 0) { calls++; throw IOException("offline") } }
        }
        assertEquals(3, calls)
        calls = 0
        assertThrows(CancellationException::class.java) {
            runBlocking { retrySourceCall(retryDelayMillis = 0) { calls++; throw CancellationException() } }
        }
        assertEquals(1, calls)
    }
}
