package io.grimoire.app.data.libraryupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LibraryWorkerPolicyTest {
    @Test
    fun `worker failures allow two retries after the initial attempt`() {
        assertTrue(shouldRetryLibraryUpdate(0))
        assertTrue(shouldRetryLibraryUpdate(1))
        assertFalse(shouldRetryLibraryUpdate(2))
        assertFalse(shouldRetryLibraryUpdate(3))
        assertFalse(shouldRetryLibraryUpdate(Int.MAX_VALUE))
    }

    @Test
    fun `progress publishes immediately and then at most once per second`() {
        val throttle = LibraryProgressThrottle()
        assertTrue(throttle.shouldPublish(0))
        assertFalse(throttle.shouldPublish(0))
        assertFalse(throttle.shouldPublish(999))
        assertTrue(throttle.shouldPublish(1_000))
        assertFalse(throttle.shouldPublish(1_999))
        assertTrue(throttle.shouldPublish(2_000))
    }

    @Test
    fun `concurrent callbacks share one progress budget`() {
        val throttle = LibraryProgressThrottle()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = List(8) {
                executor.submit<Boolean> {
                    start.await()
                    throttle.shouldPublish(0)
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
    }
}
