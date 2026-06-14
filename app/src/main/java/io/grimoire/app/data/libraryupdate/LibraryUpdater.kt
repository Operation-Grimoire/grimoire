package io.grimoire.app.data.libraryupdate

import android.util.Log
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.api.source.EpubSource
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.data.local.entity.ChapterEntity
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
import kotlinx.coroutines.delay
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
    private val athenaeum: io.grimoire.app.data.athenaeum.AthenaeumContributor,
) {

    /**
     * Refreshes every favorited novel in [categoryId], or the whole library when
     * [categoryId] is null. [onProgress] is called before each novel and once more
     * when finished.
     */
    suspend fun updateLibrary(
        categoryId: Long?,
        onProgress: suspend (done: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
        onNovelComplete: suspend (novel: NovelEntity, newReadable: Int, newLocked: Int) -> Unit = { _, _, _ -> },
    ): UpdateSummary {
        val targets = resolveTargets(categoryId)
        val total = targets.size
        // Background runs can wake the process before ExtensionManager's eager
        // scan completes; without this every novel would be flagged "Source not
        // installed".
        extensionManager.awaitReady()
        // Hoisted out of the per-novel loop: the extension list doesn't change
        // mid-sync, so reading it once avoids repeating the lookup N times.
        // Auto-download is now a per-novel flag, read off each NovelEntity below.
        val extensions = extensionManager.extensions.value
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
        val done = AtomicInteger(0)
        val newChapters = AtomicInteger(0)
        val warnings = AtomicInteger(0)
        val errors = AtomicInteger(0)

        coroutineScope {
            repeat(n) {
                launch {
                    while (true) {
                        val novel = mutex.withLock { queue.removeFirstOrNull() } ?: break
                        onProgress(done.get(), total, titleFor(novel))
                        when (val result = refreshNovel(novel, extensions)) {
                            is NovelRefreshResult.Ok -> {
                                newChapters.addAndGet(result.newChapters)
                                onNovelComplete(novel, result.newReadable, result.newLocked)
                            }
                            is NovelRefreshResult.Warned -> {
                                newChapters.addAndGet(result.newChapters)
                                warnings.incrementAndGet()
                                onNovelComplete(novel, result.newReadable, result.newLocked)
                            }
                            NovelRefreshResult.Failed -> errors.incrementAndGet()
                        }
                        done.incrementAndGet()
                    }
                }
            }
        }
        onProgress(total, total, "")
        return UpdateSummary(total, newChapters.get(), warnings.get(), errors.get())
    }

    private suspend fun resolveTargets(categoryId: Long?): List<NovelEntity> {
        val favorites = novelDao.getAll()
            .filter { it.favorite && it.sourceId != LOCAL_SOURCE_ID }
        if (categoryId == null) return favorites
        val category = categoryDao.getAllOnce().firstOrNull { it.id == categoryId }
            ?: return favorites
        // Novels in the default category are stored with a null categoryId.
        return if (category.isDefault) favorites.filter { it.categoryId == null }
        else favorites.filter { it.categoryId == categoryId }
    }

    private suspend fun refreshNovel(
        novel: NovelEntity,
        extensions: List<LoadedExtension>,
    ): NovelRefreshResult {
        val loaded = extensions.firstOrNull { it.source.id == novel.sourceId }
        if (loaded == null) {
            setIssue(novel, "", UpdateIssueSeverity.WARNING, "Source not installed — skipped")
            return NovelRefreshResult.Warned(0)
        }
        val pkg = loaded.info.packageName
        val src = loaded.source
        // EPUB sources have no scraped chapter list; their chapters arrive when the
        // user downloads the book, so there is nothing to refresh here. Clear any
        // stale issue (e.g. a "Source not installed" warning left by an earlier
        // sync that ran before the extension scan completed).
        if (src is EpubSource) {
            updateIssueDao.clearForNovel(novel.id)
            return NovelRefreshResult.Ok(0)
        }

        val fetched = runCatching {
            withRetry { src.getNovelDetails(Novel(url = novel.url, title = "")) }
        }.getOrElse { e ->
            setIssue(novel, pkg, UpdateIssueSeverity.ERROR, describeError(e))
            return NovelRefreshResult.Failed
        }

        val merged = mergeNovel(novel, fetched)
        val titleRegressed = fetched.title.isBlank() && novel.title.isNotBlank()
        val regressed = titleRegressed || coverRegressed(novel, fetched)
        novelDao.upsert(merged)

        val fetchedChapters = runCatching {
            withRetry { fetchAllChapters(src, fetched) }
        }.getOrElse { e ->
            setIssue(novel, pkg, UpdateIssueSeverity.ERROR, describeError(e))
            return NovelRefreshResult.Failed
        }

        val existing = chapterDao.getChaptersOnce(novel.id)
        if (fetchedChapters.isEmpty()) {
            return when {
                existing.isNotEmpty() -> {
                    setIssue(
                        novel, pkg, UpdateIssueSeverity.WARNING,
                        "Source returned no chapters — kept the existing list",
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
        chapterDao.replaceChapters(
            novel.id,
            fetchedChapters.mapIndexed { i, ch ->
                val prev = merge.priors[i]
                ch.toEntity(novel.id).copy(
                    read = prev?.read ?: false,
                    readProgress = prev?.readProgress ?: 0f,
                    readAnchorItemIndex = prev?.readAnchorItemIndex ?: 0,
                    readAnchorItemOffset = prev?.readAnchorItemOffset ?: 0,
                    firstReadAt = prev?.firstReadAt,
                    downloadStatus = prev?.downloadStatus ?: 0,
                    downloadedContent = prev?.downloadedContent,
                    wordCount = prev?.wordCount ?: 0,
                )
            },
        )

        // Contribute the refreshed series + chapters to Athenaeum (opt-in,
        // fire-and-forget; no-op when the toggle is off or the source isn't HTTP).
        athenaeum.submit(src, merged, chapterDao.getChaptersInReadingOrder(novel.id))

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
                    val freshChapters = chapterDao.getChaptersOnce(novel.id)
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

    /** Builds the saved novel, keeping each previous value when the fetched one is blank. */
    private fun mergeNovel(old: NovelEntity, fetched: Novel): NovelEntity = old.copy(
        title = fetched.title.takeIf { it.isNotBlank() } ?: old.title,
        thumbnailUrl = fetched.thumbnailUrl?.takeIf { it.isNotBlank() } ?: old.thumbnailUrl,
        author = fetched.author?.takeIf { it.isNotBlank() } ?: old.author,
        description = fetched.description?.takeIf { it.isNotBlank() } ?: old.description,
        genres = fetched.genres.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }?.joinToString(",") ?: old.genres,
        status = if (fetched.status != NovelStatus.UNKNOWN) fetched.status.ordinal else old.status,
        rating = fetched.rating ?: old.rating,
        ratingCount = fetched.ratingCount ?: old.ratingCount,
        language = fetched.language?.takeIf { it.isNotBlank() } ?: old.language,
        lastUpdated = System.currentTimeMillis(),
    )

    /** True when the source dropped a cover the novel previously had. */
    private fun coverRegressed(old: NovelEntity, fetched: Novel): Boolean =
        !old.thumbnailUrl.isNullOrBlank() && fetched.thumbnailUrl.isNullOrBlank()

    /**
     * Runs [block], retrying on failure up to [MAX_ATTEMPTS] times total so a brief
     * network drop does not fail an otherwise healthy novel. Cancellation is never
     * retried.
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Retry failed")
    }

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

    private fun Chapter.toEntity(novelId: Long) = ChapterEntity(
        novelId = novelId,
        url = url,
        name = name,
        uploadDate = uploadDate,
        chapterNumber = chapterNumber,
        translator = translator,
        locked = locked,
    )

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
    }

    private companion object {
        const val TAG = "LibraryUpdater"

        const val REGRESSION_MESSAGE =
            "Source returned incomplete data — kept the previous title/cover"

        /** Total fetch attempts per network call (1 initial + 2 retries). */
        const val MAX_ATTEMPTS = 3

        /** Base back-off between retries; the wait grows with each attempt. */
        const val RETRY_DELAY_MS = 2_000L

        /** Upper bound for the user-tunable sync concurrency setting. */
        const val MAX_CONCURRENCY = 8
    }
}
