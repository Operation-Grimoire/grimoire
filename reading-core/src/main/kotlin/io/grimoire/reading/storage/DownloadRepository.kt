package io.grimoire.reading.storage

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.chapterWordCount
import io.grimoire.reading.encodeChapterContent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest

data class DownloadAttempt(val key: ChapterKey, val id: String, val baseRevision: String) {
    init { require(id.isNotBlank() && baseRevision.isNotBlank()) }
}

data class DownloadReceipt(
    val key: ChapterKey,
    val attemptId: String,
    val revision: String,
    val digest: String,
    val contentVersion: Int,
    val wordCount: Int,
)

enum class DownloadRejection { STALE, MISSING, LOCKED, INELIGIBLE }
enum class DownloadFailure { ERROR, CONNECTIVITY }

sealed interface DownloadCommit {
    data class Committed(val receipt: DownloadReceipt, val replayed: Boolean) : DownloadCommit
    data class NotApplied(val reason: DownloadRejection) : DownloadCommit
}

data class PreparedDownload(val content: StoredChapterContent, val digest: String, val wordCount: Int)

/** Backend transactions must bind claim, all terminal callbacks and invalidation to attempt ownership. */
interface DownloadDao {
    suspend fun claim(key: ChapterKey): DownloadAttempt?
    suspend fun commit(attempt: DownloadAttempt, prepared: PreparedDownload): DownloadCommit
    suspend fun fail(attempt: DownloadAttempt, failure: DownloadFailure): Boolean
    suspend fun currentReceipt(key: ChapterKey): DownloadReceipt?
}

/** Shared preparation and confirmation; scheduling, eligibility and storage mechanisms remain local. */
class DownloadRepository(private val dao: DownloadDao) {
    suspend fun claim(key: ChapterKey): DownloadAttempt? {
        currentCoroutineContext().ensureActive()
        val attempt = dao.claim(key)
        currentCoroutineContext().ensureActive()
        require(attempt == null || attempt.key == key) { "Download claim identity mismatch" }
        return attempt
    }

    suspend fun commit(attempt: DownloadAttempt, pages: List<NovelPage>): DownloadCommit {
        currentCoroutineContext().ensureActive()
        val content = encodeChapterContent(pages)
        val prepared = PreparedDownload(content, downloadDigest(content), chapterWordCount(pages))
        val result = dao.commit(attempt, prepared)
        // A cancelled/lost reply does not imply rollback. Reconcile the current receipt.
        currentCoroutineContext().ensureActive()
        if (result is DownloadCommit.Committed) {
            validateReceipt(result.receipt, attempt.key)
            require(result.receipt.attemptId == attempt.id && result.receipt.digest == prepared.digest &&
                result.receipt.contentVersion == content.version &&
                (prepared.wordCount == 0 || result.receipt.wordCount == prepared.wordCount)) {
                "Download commit confirmation mismatch"
            }
        }
        return result
    }

    suspend fun fail(attempt: DownloadAttempt, failure: DownloadFailure): Boolean {
        currentCoroutineContext().ensureActive()
        val applied = dao.fail(attempt, failure)
        currentCoroutineContext().ensureActive()
        return applied
    }

    suspend fun currentReceipt(key: ChapterKey): DownloadReceipt? {
        currentCoroutineContext().ensureActive()
        val receipt = dao.currentReceipt(key)
        currentCoroutineContext().ensureActive()
        if (receipt != null) validateReceipt(receipt, key)
        return receipt
    }

    private fun validateReceipt(receipt: DownloadReceipt, key: ChapterKey) {
        require(receipt.key == key && receipt.attemptId.isNotBlank() && receipt.revision.isNotBlank() &&
            receipt.digest.matches(Regex("[a-f0-9]{64}")) && receipt.contentVersion in 1..2 && receipt.wordCount >= 0) {
            "Invalid download receipt"
        }
    }
}

fun downloadDigest(content: StoredChapterContent): String = MessageDigest.getInstance("SHA-256")
    .digest("${content.version}\u0000${content.content}".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }
