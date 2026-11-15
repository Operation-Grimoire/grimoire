package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.ChapterDownloadStateEntity
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.chapterWordCount
import io.grimoire.reading.decodeChapterContent
import io.grimoire.reading.storage.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the Android transaction boundary, without leaking Room rows or status ordinals to core. */
@Singleton
class RoomDownloadDao @Inject constructor(private val database: AppDatabase, private val owner: DownloadWriteOwner) : DownloadDao {
    private val chapters get() = database.chapterDao()
    private val states get() = database.chapterDownloadStateDao()

    /** Only called under DownloadDrain, before fetching or after all its children have exited. */
    suspend fun resetInterruptedDownloads() {
        owner.requireOwner()
        database.withTransaction { chapters.resetStuckDownloads() }
    }

    override suspend fun claim(key: ChapterKey): DownloadAttempt? {
        val ownerId = owner.requireOwner()
        return database.withTransaction {
            val chapter = chapters.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url) ?: return@withTransaction null
            if (key.novel.sourceId == LOCAL_SOURCE_ID || chapter.locked || chapter.downloadStatus !in listOf(1, 5)) return@withTransaction null
            // Legacy content receives protocol identity without rewriting its bytes or old image paths.
            val state = states.find(chapter.id) ?: ChapterDownloadStateEntity(chapter.id, UUID.randomUUID().toString())
            val id = UUID.randomUUID().toString()
            states.put(state.copy(activeAttemptId = id, activeOwner = ownerId, baseRevision = state.revision))
            chapters.setDownloadStatus(chapter.id, if (chapter.downloadStatus == 5) 6 else 2)
            DownloadAttempt(key, id, state.revision)
        }
    }

    override suspend fun commit(attempt: DownloadAttempt, prepared: PreparedDownload): DownloadCommit {
        val ownerId = owner.requireOwner()
        require(prepared.content.version == 2 && prepared.digest == downloadDigest(prepared.content) &&
            prepared.wordCount == chapterWordCount(decodeChapterContent(prepared.content))) { "Invalid prepared download" }
        return database.withTransaction {
            currentCoroutineContext().ensureActive()
            val key = attempt.key
            val chapter = chapters.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url)
                ?: return@withTransaction DownloadCommit.NotApplied(DownloadRejection.MISSING)
            if (chapter.locked) return@withTransaction DownloadCommit.NotApplied(DownloadRejection.LOCKED)
            if (key.novel.sourceId == LOCAL_SOURCE_ID) return@withTransaction DownloadCommit.NotApplied(DownloadRejection.INELIGIBLE)
            val state = states.find(chapter.id) ?: return@withTransaction DownloadCommit.NotApplied(DownloadRejection.STALE)
            if (state.committedAttemptId == attempt.id) {
                require(state.digest == prepared.digest && state.contentVersion == prepared.content.version) { "Download attempt payload conflict" }
                return@withTransaction DownloadCommit.Committed(state.receipt(key)!!, replayed = true)
            }
            if (!state.owns(attempt, ownerId) || chapter.downloadStatus !in listOf(2, 6)) {
                return@withTransaction DownloadCommit.NotApplied(DownloadRejection.STALE)
            }
            // Validate prior evidence while this attempt owns failure reporting. A bad refresh
            // becomes its guarded error, rather than trapping the whole queue during claim.
            val prior = chapters.getContent(chapter.id)
            require(chapter.downloadStatus != 6 || prior != null) { "Saved refresh content is missing" }
            if (prior != null) {
                decodeChapterContent(prior)
                require(state.digest == null || state.digest == downloadDigest(prior)) { "Saved download digest mismatch" }
            }
            val revision = UUID.randomUUID().toString()
            val words = prepared.wordCount.takeIf { it > 0 } ?: chapter.wordCount
            require(words >= 0) { "Invalid saved word count" }
            chapters.setDownloadedContent(chapter.id, prepared.content, 3)
            // Content/status triggers revoke old ownership first; receipt publication is in this same transaction.
            val committed = ChapterDownloadStateEntity(chapter.id, revision,
                committedAttemptId = attempt.id, digest = prepared.digest, contentVersion = prepared.content.version,
                wordCount = words, cacheRevision = revision)
            states.put(committed)
            DownloadCommit.Committed(committed.receipt(key)!!, replayed = false)
        }
    }

    override suspend fun fail(attempt: DownloadAttempt, failure: DownloadFailure): Boolean {
        val ownerId = owner.requireOwner()
        return database.withTransaction {
            val key = attempt.key
            val chapter = chapters.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url) ?: return@withTransaction false
            val state = states.find(chapter.id) ?: return@withTransaction false
            if (chapter.locked || !state.owns(attempt, ownerId) || chapter.downloadStatus !in listOf(2, 6)) return@withTransaction false
            val refresh = chapter.downloadStatus == 6
            val status = when (failure) {
                DownloadFailure.ERROR -> if (refresh) 7 else 4
                DownloadFailure.CONNECTIVITY -> if (refresh) 5 else 1
            }
            chapters.setDownloadStatus(chapter.id, status)
            true
        }
    }

    override suspend fun currentReceipt(key: ChapterKey): DownloadReceipt? = database.withTransaction {
        val chapter = chapters.getByNovelUrl(key.novel.sourceId, key.novel.url, key.url) ?: return@withTransaction null
        if (chapter.locked) return@withTransaction null
        states.find(chapter.id)?.receipt(key)
    }

    private fun ChapterDownloadStateEntity.owns(attempt: DownloadAttempt, ownerId: String) =
        activeAttemptId == attempt.id && activeOwner == ownerId && baseRevision == attempt.baseRevision && revision == attempt.baseRevision

    private fun ChapterDownloadStateEntity.receipt(key: ChapterKey): DownloadReceipt? = committedAttemptId?.let {
        DownloadReceipt(key, it, revision, requireNotNull(digest), requireNotNull(contentVersion), requireNotNull(wordCount))
    }
}
