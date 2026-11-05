package io.grimoire.app.data.libraryupdate

internal const val MAX_LIBRARY_UPDATE_ATTEMPTS = 3

// WorkManager counts previous attempts, starting at zero.
internal fun shouldRetryLibraryUpdate(runAttemptCount: Int): Boolean =
    runAttemptCount < MAX_LIBRARY_UPDATE_ATTEMPTS - 1

/** Shared by sync and download progress callbacks, which may run concurrently. */
internal class LibraryProgressThrottle {
    private var lastPublishedAt: Long? = null

    @Synchronized
    fun shouldPublish(nowMillis: Long): Boolean {
        val previous = lastPublishedAt
        if (previous != null && nowMillis - previous < 1_000L) return false
        lastPublishedAt = nowMillis
        return true
    }
}
