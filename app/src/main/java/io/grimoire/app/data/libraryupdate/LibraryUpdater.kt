package io.grimoire.app.data.libraryupdate

import android.util.Log
import androidx.room.withTransaction
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.epub.EpubSource
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.UpdateIssueEntity
import io.grimoire.app.data.local.entity.UpdateIssueSeverity
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.data.source.fetchAllChapters
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.LoadedExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Aggregate outcome of a library refresh run. */
data class UpdateSummary(
    val novelsChecked: Int,
    val newChapters: Int,
    val warnings: Int,
    val errors: Int,
)

/**
 * Refreshes library novels from their sources, fetching new chapters while never
 * overwriting good data with broken data: if a source throws or returns blank
 * fields, the previous title/cover/chapters are kept and the problem is recorded
 * in [UpdateIssueDao] so the user can see it on the warnings page.
 */
@Singleton
class LibraryUpdater @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val libraryUpdateDao: LibraryUpdateDao,
    private val updateIssueDao: UpdateIssueDao,
    private val extensionManager: ExtensionManager,
    private val preferences: LibraryUpdatePreferences,
    private val downloadManager: DownloadManager,
    private val authManager: HiddenCategoriesAuthManager,
    private val database: AppDatabase,
) {
    private val runMutex = Mutex()

    /**
     * Refreshes every favorited novel in [categoryId], or the whole library when
     * [categoryId] is null. [onProgress] is called before each novel and once more
     * when finished.
     */
    suspend fun updateLibrary(
        categoryId: Long?,
        onProgress: suspend (done: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
        onNovelComplete: suspend (novel: NovelEntity, newReadable: Int, newLocked: Int) -> Unit = { _, _, _ -> },
    ): UpdateSummary = runMutex.withLock {
        updateLibraryLocked(categoryId, onProgress, onNovelComplete)
    }

    private suspend fun updateLibraryLocked(
        categoryId: Long?,
        onProgress: suspend (done: Int, total: Int, title: String) -> Unit,
        onNovelComplete: suspend (novel: NovelEntity, newReadable: Int, newLocked: Int) -> Unit,
    ): UpdateSummary {
        // Background runs can wake the process before ExtensionManager's eager
        // scan completes; without this every novel would be flagged "Source not
        // installed". Awaited before resolving targets so the EPUB-source check
        // below can read each novel's loaded source type.
        extensionManager.awaitReady()
        // Hoisted out of the per-novel loop: the extension list doesn't change
        // mid-sync, so reading it once avoids repeating the lookup N times.
        // Auto-download is now a per-novel flag, read off each NovelEntity below.
        val extensions = extensionManager.extensions.value
        // EPUB-source novels (extensions like Z-Library / libgen) have no scraped
        // chapter list to refresh, so they're dropped from the run by default — they'd
        // only no-op or warn. The user can opt them back in. Local file imports are
        // always excluded in resolveTargets.
        val includeEpubs = preferences.includeEpubsInSync.changes().first()
        val targets = resolveTargets(categoryId).let { list ->
            list.filter { shouldSyncNovel(it) && (includeEpubs || !isEpubNovel(it, extensions)) }
        }
        val total = targets.size
        val n = preferences.concurrency.changes().first().coerceIn(1, MAX_CONCURRENCY)
        // Snapshot hidden categories once per run; per-call read of authManager.isUnlocked.value
        // lets a mid-sync unlock/lock toggle take effect on subsequent progress emissions.
        val hiddenCategoryIds = categoryDao.getAllOnce()
            .filter { it.isHidden }.map { it.id }.toSet()
        fun titleFor(novel: NovelEntity): String =
            if (!authManager.isUnlocked.value && novel.categoryId in hiddenCategoryIds) ""
            else novel.title

        val queue = ArrayDeque(targets)
        val mutex = Mutex()
        val sourceGates = targets.map { it.sourceId }.distinct().associateWith { SourceSyncGate() }
        val done = AtomicInteger(0)
        val newChapters = AtomicInteger(0)
        val warnings = AtomicInteger(0)
        val errors = AtomicInteger(0)

        coroutineScope {
            repeat(n) {
                launch {
                    while (true) {
                        val novel = mutex.withLock { queue.removeFirstOrNull() } ?: break
                        try {
                            val current = novelDao.getById(novel.id) ?: continue
                            onProgress(done.get(), total, titleFor(current))
                            val result = sourceGates.getValue(novel.sourceId).run(
                                outcome = {
                                    when (it) {
                                        NovelRefreshResult.Failed -> SourceSyncOutcome.FAILURE
                                        NovelRefreshResult.Skipped -> SourceSyncOutcome.SKIPPED
                                        else -> SourceSyncOutcome.SUCCESS
                                    }
                                },
                            ) {
                                refreshNovel(current, extensions, requireFavorite = true)
                            } ?: run {
                                val latest = novelDao.getById(novel.id)
                                if (latest == null || !isEligibleForLibrarySync(latest)) {
                                    NovelRefreshResult.Skipped
                                } else {
                                    setIssue(
                                        latest,
                                        extensions.firstOrNull { it.id == latest.sourceId }
                                            ?.info?.packageName.orEmpty(),
                                        UpdateIssueSeverity.ERROR,
                                        ISSUE_SOURCE_PAUSED,
                                    )
                                    NovelRefreshResult.Failed
                                }
                            }
                            when (result) {
                                is NovelRefreshResult.Ok -> {
                                    newChapters.addAndGet(result.newChapters)
                                    novelDao.getById(novel.id)?.takeIf { it.favorite }?.let {
                                        onNovelComplete(it, result.newReadable, result.newLocked)
                                    }
                                }
                                is NovelRefreshResult.Warned -> {
                                    newChapters.addAndGet(result.newChapters)
                                    warnings.incrementAndGet()
                                    novelDao.getById(novel.id)?.takeIf { it.favorite }?.let {
                                        onNovelComplete(it, result.newReadable, result.newLocked)
                                    }
                                }
                                NovelRefreshResult.Failed -> errors.incrementAndGet()
                                NovelRefreshResult.Skipped -> Unit
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // A failed DB write must not restart the whole library.
                            Log.e(TAG, "Refresh failed for novel ${novel.id}", e)
                            errors.incrementAndGet()
                        } finally {
                            done.incrementAndGet()
                        }
                    }
                }
            }
        }
        onProgress(total, total, "")
        return UpdateSummary(total, newChapters.get(), warnings.get(), errors.get())
    }

    /**
     * True when [novel]'s content is EPUB-based — either a local file import or an
     * [EpubSource]-backed extension (e.g. Z-Library, libgen). Such novels have no
     * scraped chapter list, so a sync run can't fetch anything new for them. An
     * uninstalled source can't be classified and is treated as non-EPUB.
     */
    private fun isEpubNovel(novel: NovelEntity, extensions: List<LoadedExtension>): Boolean =
        novel.sourceId == LOCAL_SOURCE_ID ||
            extensions.firstOrNull { it.id == novel.sourceId }?.source is EpubSource

    private suspend fun resolveTargets(categoryId: Long?): List<NovelEntity> {
        // Local file imports (LOCAL_SOURCE_ID) have no backing source at all, so
        // they can never be synced and are dropped here regardless of the
        // include-EPUBs preference; that toggle only governs EPUB-source extensions.
        val favorites = novelDao.getAll()
            .filter { it.favorite && it.sourceId != LOCAL_SOURCE_ID }
        if (categoryId == null) return favorites
        val category = categoryDao.getAllOnce().firstOrNull { it.id == categoryId }
            ?: return favorites
        // Novels in the default category are stored with a null categoryId.
        return if (category.isDefault) favorites.filter { it.categoryId == null }
        else favorites.filter { it.categoryId == categoryId }
    }

    /**
     * Refreshes a single novel — the retry action on the update-warnings page.
     * A clean refresh clears the novel's issue row; a failing one re-records
     * it with the fresh error. Returns false when the refresh failed outright.
     */
    suspend fun updateNovel(novelId: Long): Boolean = runMutex.withLock {
        // Full rescan, not just awaitReady: a "source not installed" issue is
        // retried right after the user installs the extension, and the retry
        // must see the fresh package set. Cheap at single-novel scale.
        extensionManager.refresh()
        val novel = novelDao.getById(novelId) ?: return@withLock false
        val result = refreshNovel(novel, extensionManager.extensions.value)
        result is NovelRefreshResult.Ok || result is NovelRefreshResult.Warned
    }

    private suspend fun refreshNovel(
        snapshot: NovelEntity,
        extensions: List<LoadedExtension>,
        requireFavorite: Boolean = false,
    ): NovelRefreshResult {
        var novel = novelDao.getById(snapshot.id) ?: return NovelRefreshResult.Skipped
        if (requireFavorite && !isEligibleForLibrarySync(novel)) return NovelRefreshResult.Skipped
        val loaded = extensions.firstOrNull { it.id == novel.sourceId }
        val src = loaded?.source
        if (loaded == null || src == null) {
            setIssue(novel, "", UpdateIssueSeverity.WARNING, "Source not installed — skipped")
            return NovelRefreshResult.Warned(0)
        }
        val pkg = loaded.info.packageName
        // EPUB sources have no scraped chapter list; their chapters arrive when the
        // user downloads the book, so there is nothing to refresh here. Clear any
        // stale issue (e.g. a "Source not installed" warning left by an earlier
        // sync that ran before the extension scan completed).
        if (src is EpubSource) {
            updateIssueDao.clearForNovel(novel.id)
            return NovelRefreshResult.Ok(0)
        }

        val fetched = runCatching {
            retrySourceCall { src.getNovelDetails(Novel(url = novel.url, title = "", language = Language.UNKNOWN)) }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            setIssue(novel, pkg, UpdateIssueSeverity.ERROR, describeError(e))
            return NovelRefreshResult.Failed
        }

        novel = novelDao.getById(novel.id) ?: return NovelRefreshResult.Skipped
        if (requireFavorite && !isEligibleForLibrarySync(novel)) return NovelRefreshResult.Skipped

        val fetchedChapters = runCatching {
            retrySourceCall { fetchAllChapters(src, fetched, window = 2) }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            setIssue(novel, pkg, UpdateIssueSeverity.ERROR, describeError(e))
            return NovelRefreshResult.Failed
        }

        // Read and merge atomically after both network waits, so local edits cannot
        // be replaced by the start-of-run snapshot.
        val merged = database.withTransaction {
            val current = novelDao.getById(novel.id) ?: return@withTransaction null
            if (requireFavorite && !isEligibleForLibrarySync(current)) return@withTransaction null
            val updated = mergeNovelMetadata(current, fetched, System.currentTimeMillis())
            if (updated != current) novelDao.upsert(updated)
            updated
        } ?: return NovelRefreshResult.Skipped
        novel = merged
        val titleRegressed = fetched.title.isBlank() && novel.title.isNotBlank()
        val regressed = titleRegressed || coverRegressed(novel, fetched)

        // Metadata only — never the per-chapter downloaded text. The merge
        // matches on url/name/read state, and the reconcile below preserves any
        // downloaded text in the DB without it ever passing through app memory.
        // Loading full content for every chapter of every novel syncing in parallel
        // is what exhausted the heap on a heavily-downloaded library.
        val existing = chapterDao.getChapterMetadataOnce(novel.id)
        if (fetchedChapters.isEmpty()) {
            return when {
                existing.isNotEmpty() -> {
                    setIssue(
                        novel, pkg, UpdateIssueSeverity.WARNING,
                        ISSUE_NO_CHAPTERS,
                    )
                    NovelRefreshResult.Warned(0)
                }
                regressed -> {
                    setIssue(novel, pkg, UpdateIssueSeverity.WARNING, REGRESSION_MESSAGE)
                    NovelRefreshResult.Warned(0)
                }
                else -> {
                    updateIssueDao.clearForNovel(novel.id)
                    NovelRefreshResult.Ok(0)
                }
            }
        }

        val merge = matchChapters(existing, fetchedChapters)
        if (merge.matchedByName > 0 || merge.droppedRead > 0) {
            // Field diagnostics for read-state-migration reports (#138): a
            // name-pass rescue means the source rewrote chapter URLs; dropped
            // read rows mean state was about to vanish with no match at all.
            // Novel id only — titles can belong to hidden categories.
            Log.w(
                TAG,
                "Chapter merge for novel ${novel.id}: byUrl=${merge.matchedByUrl}" +
                    " byName=${merge.matchedByName}" +
                    " new=${fetchedChapters.size - merge.matchedByUrl - merge.matchedByName}" +
                    " readStateDropped=${merge.droppedRead}",
            )
        }
        // Reconcile in place: matched rows keep their id, read state, and downloaded
        // content; only metadata is rewritten. New chapters are inserted, vanished
        // ones deleted. No downloaded text is read or written here.
        val plan = buildReconcilePlan(novel.id, existing, fetchedChapters, merge)
        // A fetch that would wipe most of an established list is far more likely a
        // silently truncated response (throttle page mid-pagination) than a real
        // mass removal — deleting would destroy read state and downloads, and the
        // next healthy sync would re-detect everything as "new". Keep the list.
        if (isSuspectTruncation(existing.size, plan.deleteIds.size)) {
            Log.w(TAG, "Kept list for novel ${novel.id}: fetched=${fetchedChapters.size} existing=${existing.size}")
            setIssue(novel, pkg, UpdateIssueSeverity.WARNING, ISSUE_CHAPTER_LIST_CHANGED)
            return NovelRefreshResult.Warned(0)
        }
        chapterDao.reconcileChapters(plan.deleteIds, plan.updates, plan.inserts)

        // Only log new chapters when the novel already had a chapter list, so the
        // first refresh of a never-opened novel does not flood the log. A chapter
        // that flipped from locked to unlocked also counts as an update — the
        // user couldn't read it before and now they can.
        val newChapters = if (existing.isEmpty()) {
            emptyList()
        } else {
            fetchedChapters.filterIndexed { i, ch ->
                val prev = merge.priors[i]
                prev == null || (prev.locked && !ch.locked)
            }
        }
        // Feed cap: a per-novel burst above MAX_LOGGED_NEW_CHAPTERS is chapters
        // being re-detected after an earlier bad sync, not a real release. The DB
        // was reconciled above either way; only the feed/auto-download side is
        // suppressed so the updates list and download queue don't flood.
        if (newChapters.size > MAX_LOGGED_NEW_CHAPTERS) {
            setIssue(
                novel, pkg, UpdateIssueSeverity.WARNING,
                "${newChapters.size} chapters appeared at once — update entries suppressed " +
                    "(source likely recovered from an earlier bad sync)",
            )
            return NovelRefreshResult.Warned(0)
        }
        if (newChapters.isNotEmpty()) {
            val priorByUrl = fetchedChapters.indices.asSequence()
                .mapNotNull { i -> merge.priors[i]?.let { fetchedChapters[i].url to it } }
                .toMap()
            val now = System.currentTimeMillis()
            libraryUpdateDao.insertAll(
                newChapters.map { ch ->
                    val prev = priorByUrl[ch.url]
                    LibraryUpdateEntity(
                        novelId = novel.id,
                        sourcePackage = pkg,
                        novelUrl = novel.url,
                        novelTitle = merged.title,
                        novelThumbnailUrl = merged.thumbnailUrl,
                        chapterUrl = ch.url,
                        chapterName = ch.name,
                        chapterNumber = ch.chapterNumber,
                        foundAt = now,
                        locked = ch.locked,
                        unlockedFromLocked = prev != null && prev.locked && !ch.locked,
                    )
                },
            )
            if (novel.autoDownloadNewChapters) {
                // Locked chapters can't be fetched; skip them so the queue isn't
                // poisoned with chapters that will just fail.
                val downloadableUrls = newChapters
                    .filterNot { it.locked }
                    .map { it.url }
                    .toSet()
                if (downloadableUrls.isNotEmpty()) {
                    val freshChapters = chapterDao.getChapterMetadataOnce(novel.id)
                        .filter { it.url in downloadableUrls }
                    // startService = false: the worker drains this queue inline
                    // after the sync, so we must not spawn DownloadService and its
                    // competing download notification mid-sync.
                    if (freshChapters.isNotEmpty()) {
                        downloadManager.enqueue(freshChapters, startService = false)
                    }
                }
            }
        }

        val newLocked = newChapters.count { it.locked }
        val newReadable = newChapters.size - newLocked
        return if (regressed) {
            setIssue(novel, pkg, UpdateIssueSeverity.WARNING, REGRESSION_MESSAGE)
            NovelRefreshResult.Warned(newChapters.size, newReadable, newLocked)
        } else {
            updateIssueDao.clearForNovel(novel.id)
            NovelRefreshResult.Ok(newChapters.size, newReadable, newLocked)
        }
    }

    /** True when the source dropped a cover the novel previously had. */
    private fun coverRegressed(old: NovelEntity, fetched: Novel): Boolean =
        !old.thumbnailUrl.isNullOrBlank() && fetched.thumbnailUrl.isNullOrBlank()

    private suspend fun setIssue(
        novel: NovelEntity,
        pkg: String,
        severity: UpdateIssueSeverity,
        message: String,
    ) {
        updateIssueDao.setIssue(
            UpdateIssueEntity(
                novelId = novel.id,
                sourcePackage = pkg,
                novelUrl = novel.url,
                novelTitle = novel.title,
                severity = severity.ordinal,
                message = message,
                occurredAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun describeError(e: Throwable): String =
        "${e::class.simpleName}: ${e.message ?: "(no message)"}"

    private sealed interface NovelRefreshResult {
        val newChapters: Int get() = 0
        val newReadable: Int get() = 0
        val newLocked: Int get() = 0

        data class Ok(
            override val newChapters: Int,
            override val newReadable: Int = newChapters,
            override val newLocked: Int = 0,
        ) : NovelRefreshResult
        data class Warned(
            override val newChapters: Int,
            override val newReadable: Int = newChapters,
            override val newLocked: Int = 0,
        ) : NovelRefreshResult
        data object Failed : NovelRefreshResult
        data object Skipped : NovelRefreshResult
    }

    private companion object {
        const val TAG = "LibraryUpdater"

        const val REGRESSION_MESSAGE =
            "Source returned incomplete data — kept the previous title/cover"

        /** Upper bound for the user-tunable sync concurrency setting. */
        const val MAX_CONCURRENCY = 8
    }
}
