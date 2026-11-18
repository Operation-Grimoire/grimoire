package io.grimoire.reading

import io.grimoire.reading.storage.OpenedHistoryDao
import io.grimoire.reading.storage.OpenedHistoryWrites
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class OpenedHistoryWritesTest {
    private class Store : OpenedHistoryDao<String, String> {
        val entries = linkedMapOf<String, Long>()
        val calls = mutableListOf<String>()
        var failWrites = false

        override suspend fun record(snapshot: String, openedAtMillis: Long) {
            calls += "open $snapshot"
            if (failWrites) throw IOException("Unable to save")
            entries[snapshot] = openedAtMillis
        }

        override suspend fun delete(recordIds: List<String>) {
            calls += "delete ${recordIds.joinToString()}"
            entries.keys.removeAll(recordIds.toSet())
        }

        override suspend fun clear() {
            calls += "clear"
            entries.clear()
        }
    }

    private fun verify(action: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(5_000) { coroutineScope(action) }
    }

    @Test fun clearRunsAfterDelayedOpenAndBeforeNewOpen() = verify {
        val store = Store()
        val owner = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        try {
            val writes = OpenedHistoryWrites(owner, store)
            val entered = CompletableDeferred<Unit>()
            val resolved = CompletableDeferred<String>()
            val old = writes.open(false, 100) { entered.complete(Unit); resolved.await() }!!
            entered.await()
            val clear = writes.clear()
            val newer = writes.open(false, 300) { "new" }!!
            assertFalse(clear.isCompleted)
            assertFalse(newer.isCompleted)
            resolved.complete("old")
            old.await().getOrThrow()
            clear.await().getOrThrow()
            newer.await().getOrThrow()
            assertEquals(listOf("open old", "clear", "open new"), store.calls)
            assertEquals(mapOf("new" to 300L), store.entries)
        } finally {
            owner.cancel()
        }
    }

    @Test fun capturesDeletionSelectionAndNeverResolvesPrivateOpen() = verify {
        val store = Store()
        val owner = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        try {
            val writes = OpenedHistoryWrites(owner, store)
            val resolved = CompletableDeferred<String>()
            val old = writes.open(false, 100) { resolved.await() }!!
            assertNull(writes.open(true, 200) { error("Private open must never resolve metadata") })
            val ids = mutableListOf("old", "old")
            val delete = writes.delete(ids)!!
            ids.clear()
            val newer = writes.open(false, 300) { "old" }!!
            resolved.complete("old")
            old.await().getOrThrow()
            delete.await().getOrThrow()
            newer.await().getOrThrow()
            assertEquals(listOf("open old", "delete old", "open old"), store.calls)
            assertEquals(mapOf("old" to 300L), store.entries)
        } finally {
            owner.cancel()
        }
    }

    @Test fun failedAndCancelledOpensDoNotBlockClear() = verify {
        val store = Store().apply { failWrites = true }
        val owner = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
        try {
            val errors = mutableListOf<Exception>()
            val writes = OpenedHistoryWrites(owner, store) { errors.add(it) }
            val failure = writes.open(false, 100) { "failed" }!!
            val cancelled = writes.open(false, 200) { throw CancellationException("Reader left") }!!
            val clear = writes.clear()
            assertTrue(failure.await().exceptionOrNull() is IOException)
            cancelled.join()
            assertTrue(cancelled.isCancelled)
            clear.await().getOrThrow()
            assertEquals(1, errors.size)
            assertEquals(listOf("open failed", "clear"), store.calls)
            assertTrue(store.entries.isEmpty())
        } finally {
            owner.cancel()
        }
    }
}
