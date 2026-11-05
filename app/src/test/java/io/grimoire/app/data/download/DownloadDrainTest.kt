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
