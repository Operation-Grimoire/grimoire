package io.grimoire.app.data.download

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex

/** Serializes service and inline-worker drains, including initialization. */
internal class DownloadDrain {
    private val mutex = Mutex()

    suspend fun <T : Any> run(
        waitForTurn: Boolean,
        onExit: suspend () -> Unit = {},
        block: suspend () -> T,
    ): T? {
        if (waitForTurn) mutex.lock() else if (!mutex.tryLock()) return null
        try {
            return block()
        } finally {
            try {
                withContext(NonCancellable) { onExit() }
            } finally {
                mutex.unlock()
            }
        }
    }
}

internal class DownloadProgressThrottle(private val now: () -> Long = System::nanoTime) {
    private var lastReport: Long? = null

    @Synchronized
    fun shouldReport(): Boolean {
        val current = now()
        if (lastReport?.let { current - it < 1_000_000_000L } == true) return false
        lastReport = current
        return true
    }
}

/** Registers writes before returning, then does the work off the caller thread. */
internal class DownloadQueueWrites(private val scope: CoroutineScope) {
    private val mutex = Mutex()

    fun submit(block: suspend () -> Unit) = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        mutex.withLock { withContext(Dispatchers.IO) { block() } }
    }

    suspend fun await() {
        mutex.withLock { }
    }
}

/** Coalesces accepted user requests without letting rejected batches start a drain. */
internal class DownloadServiceRequests {
    private val pending = AtomicBoolean(false)

    fun allow() { pending.set(true) }
    fun clear() { pending.set(false) }

    suspend fun <T : Any> run(awaitWrites: suspend () -> Unit, drain: suspend () -> T): T? {
        awaitWrites()
        if (!pending.getAndSet(false)) return null
        return drain()
    }
}
