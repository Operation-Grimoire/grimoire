package io.grimoire.app.data.download

import kotlinx.coroutines.sync.Mutex

/** Serializes service and inline-worker drains, including initialization. */
internal class DownloadDrain {
    private val mutex = Mutex()

    suspend fun <T : Any> run(waitForTurn: Boolean, block: suspend () -> T): T? {
        if (waitForTurn) mutex.lock() else if (!mutex.tryLock()) return null
        try {
            return block()
        } finally {
            mutex.unlock()
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
