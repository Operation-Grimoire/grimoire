package io.grimoire.app.data.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadDrainTest {
    @Test
    fun cancellationDuringInitializationReleasesDrain() = runBlocking {
        val drain = DownloadDrain()
        val entered = CompletableDeferred<Unit>()
        val initialization = CompletableDeferred<Unit>()
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            drain.run(false) {
                entered.complete(Unit)
                initialization.await()
            }
        }
        entered.await()
        assertNull(drain.run(false) { "overlap" })
        worker.cancelAndJoin()
        assertEquals("recovered", drain.run(false) { "recovered" })
    }

    @Test
    fun serviceWaitsForInlineDrainThenProcessesNewWork() = runBlocking {
        val drain = DownloadDrain()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            drain.run(false) { release.await(); events += "worker" }
        }
        val service = launch(start = CoroutineStart.UNDISPATCHED) {
            drain.run(true) { events += "service" }
        }
        assertTrue(events.isEmpty())
        release.complete(Unit)
        worker.join()
        service.join()
        assertEquals(listOf("worker", "service"), events)
    }

    @Test
    fun cancellingWaitingServiceDoesNotReleaseWorkersDrain() = runBlocking {
        val drain = DownloadDrain()
        val release = CompletableDeferred<Unit>()
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            drain.run(false) { release.await() }
        }
        val service = launch(start = CoroutineStart.UNDISPATCHED) { drain.run(true) {} }
        service.cancelAndJoin()
        assertNull(drain.run(false) { "overlap" })
        release.complete(Unit)
        worker.join()
        assertEquals("ready", drain.run(false) { "ready" })
    }

    @Test
    fun cancellationCleansUpBeforeAnotherDrainCanStart() = runBlocking {
        val drain = DownloadDrain()
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            drain.run(false, onExit = {
                assertNull(drain.run(false) { "overlap" })
                cleaned = true
            }) {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        entered.await()
        worker.cancelAndJoin()
        assertTrue(cleaned)
        assertEquals("ready", drain.run(false) { "ready" })
    }

    @Test
    fun immediateServiceDrainWaitsForRegisteredQueueWrites() = runBlocking {
        val writes = DownloadQueueWrites(this)
        val allowWrite = CompletableDeferred<Unit>()
        val persisted = CompletableDeferred<Unit>()
        val callerThread = Thread.currentThread()
        val writer = writes.submit {
            assertNotSame(callerThread, Thread.currentThread())
            allowWrite.await()
            persisted.complete(Unit)
        }
        val service = launch(start = CoroutineStart.UNDISPATCHED) {
            writes.await()
            assertTrue(persisted.isCompleted)
        }
        assertFalse(service.isCompleted)
        allowWrite.complete(Unit)
        writer.join()
        service.join()
    }

    @Test
    fun rejectedBatchDoesNotAuthorizeUnrelatedQueuedWork() = runBlocking {
        val requests = DownloadServiceRequests()
        var drains = 0
        assertNull(requests.run({}) { drains++; "unexpected" })
        assertEquals(0, drains)
    }

    @Test
    fun acceptedRequestWaitsForWritesAndIsConsumedOnlyOnce() = runBlocking {
        val requests = DownloadServiceRequests()
        val writes = DownloadQueueWrites(this)
        val allowWrite = CompletableDeferred<Unit>()
        writes.submit { allowWrite.await(); requests.allow() }
        var drains = 0
        val service = launch(start = CoroutineStart.UNDISPATCHED) {
            requests.run(writes::await) { drains++; "accepted" }
        }
        assertEquals(0, drains)
        allowWrite.complete(Unit)
        service.join()
        assertEquals(1, drains)
        assertNull(requests.run(writes::await) { drains++; "replayed" })
        assertEquals(1, drains)
    }

    @Test
    fun explicitPauseRevokesPendingServiceWork() = runBlocking {
        val requests = DownloadServiceRequests()
        requests.allow()
        requests.clear()
        assertNull(requests.run({}) { "unexpected" })
    }

    @Test
    fun progressReportsImmediatelyThenAtMostOncePerSecond() {
        var time = 0L
        val throttle = DownloadProgressThrottle { time }
        assertTrue(throttle.shouldReport())
        repeat(100) { assertFalse(throttle.shouldReport()) }
        time = 999_999_999L
        assertFalse(throttle.shouldReport())
        time = 1_000_000_000L
        assertTrue(throttle.shouldReport())
    }
}
