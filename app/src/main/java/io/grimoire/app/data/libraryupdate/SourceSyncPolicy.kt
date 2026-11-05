package io.grimoire.app.data.libraryupdate

import io.grimoire.api.network.CloudflareException
import io.grimoire.app.data.source.TruncatedChapterListException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

internal enum class SourceSyncOutcome { SUCCESS, FAILURE, SKIPPED }

// Reuse the existing localized source-unavailable message in Update Issues.
internal const val ISSUE_SOURCE_PAUSED = "Source not available"

/** One novel at a time per source; a failing source is paused for the rest of this run. */
internal class SourceSyncGate(private val failureLimit: Int = 3) {
    private val semaphore = Semaphore(1)
    private var consecutiveFailures = 0

    suspend fun <T> run(outcome: (T) -> SourceSyncOutcome, block: suspend () -> T): T? =
        semaphore.withPermit {
            if (consecutiveFailures >= failureLimit) return@withPermit null
            val result = block()
            when (outcome(result)) {
                SourceSyncOutcome.SUCCESS -> consecutiveFailures = 0
                SourceSyncOutcome.FAILURE -> consecutiveFailures++
                SourceSyncOutcome.SKIPPED -> Unit
            }
            result
        }
}

// Extensions report HTTP failures as messages; the API has no typed HTTP exception.
private val httpStatus = Regex("""\bHTTP\s+([1-5]\d{2})\b""", RegexOption.IGNORE_CASE)

internal fun isRetryableSourceFailure(error: Throwable): Boolean {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    if (causes.any { it is CancellationException || it is CloudflareException }) return false
    val status = causes.firstNotNullOfOrNull { cause ->
        httpStatus.find(cause.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
    }
    return if (status != null) {
        status == 408 || status == 429 || status >= 500
    } else {
        causes.any { it is IOException || it is TruncatedChapterListException }
    }
}

internal suspend fun <T> retrySourceCall(
    retryDelayMillis: Long = 2_000L,
    block: suspend () -> T,
): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt >= 3 || !isRetryableSourceFailure(e)) throw e
            delay(retryDelayMillis * attempt)
            attempt++
        }
    }
}
