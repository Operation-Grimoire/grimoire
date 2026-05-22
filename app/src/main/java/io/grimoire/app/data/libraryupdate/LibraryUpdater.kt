package io.grimoire.app.data.libraryupdate

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.api.source.EpubSource
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
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
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
) {

    /**
     * Refreshes every favorited novel in [categoryId], or the whole library when
     * [categoryId] is null. [onProgress] is called before each novel and once more
     * when finished.
     */
    suspend fun updateLibrary(
        categoryId: Long?,
        onProgress: (done: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
    ): UpdateSummary {
        val targets = resolveTargets(categoryId)
        var newChapters = 0
        var warnings = 0
        var errors = 0
        targets.forEachIndexed { index, novel ->
            onProgress(index, targets.size, novel.title)
            when (val result = refreshNovel(novel)) {
                is NovelRefreshResult.Ok -> newChapters += result.newChapters
                is NovelRefreshResult.Warned -> {
                    newChapters += result.newChapters
                    warnings++
                }
                NovelRefreshResult.Failed -> errors++
            }
        }
        onProgress(targets.size, targets.size, "")
        return UpdateSummary(targets.size, newChapters, warnings, errors)
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

    private suspend fun refreshNovel(novel: NovelEntity): NovelRefreshResult {
        val loaded = extensionManager.extensions.value
            .firstOrNull { it.source.id == novel.sourceId }
        if (loaded == null) {
            setIssue(novel, "", UpdateIssueSeverity.WARNING, "Source not installed — skipped")
            return NovelRefreshResult.Warned(0)
        }
        val pkg = loaded.info.packageName
        val src = loaded.source
        // EPUB sources have no scraped chapter list; their chapters arrive when the
        // user downloads the book, so there is nothing to refresh here.
        if (src is EpubSource) return NovelRefreshResult.Ok(0)

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

        val existingByUrl = existing.associateBy { it.url }
        chapterDao.replaceChapters(
            novel.id,
            fetchedChapters.map { ch ->
                val prev = existingByUrl[ch.url]
                ch.toEntity(novel.id).copy(
                    read = prev?.read ?: false,
                    readProgress = prev?.readProgress ?: 0f,
                    firstReadAt = prev?.firstReadAt,
                    downloadStatus = prev?.downloadStatus ?: 0,
                    downloadedContent = prev?.downloadedContent,
                    wordCount = prev?.wordCount ?: 0,
                )
            },
        )

        // Only log new chapters when the novel already had a chapter list, so the
        // first refresh of a never-opened novel does not flood the log.
        val newChapters = if (existing.isEmpty()) {
            emptyList()
        } else {
            fetchedChapters.filter { it.url !in existingByUrl }
        }
        if (newChapters.isNotEmpty()) {
            val now = System.currentTimeMillis()
            libraryUpdateDao.insertAll(
                newChapters.map { ch ->
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
                    )
                },
            )
        }

        return if (regressed) {
            setIssue(novel, pkg, UpdateIssueSeverity.WARNING, REGRESSION_MESSAGE)
            NovelRefreshResult.Warned(newChapters.size)
        } else {
            updateIssueDao.clearForNovel(novel.id)
            NovelRefreshResult.Ok(newChapters.size)
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

    private suspend fun fetchAllChapters(src: Source, novel: Novel): List<Chapter> {
        if (src !is PaginatedSource) return src.getChapterList(novel)
        val all = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        var page = 1
        while (true) {
            val batch = src.getChapterList(novel, page)
            if (batch.isEmpty()) break
            val new = batch.filter { seen.add(it.url) }
            if (new.isEmpty()) break
            all += new
            page++
        }
        return all
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
        data class Ok(val newChapters: Int) : NovelRefreshResult
        data class Warned(val newChapters: Int) : NovelRefreshResult
        data object Failed : NovelRefreshResult
    }

    private companion object {
        const val REGRESSION_MESSAGE =
            "Source returned incomplete data — kept the previous title/cover"

        /** Total fetch attempts per network call (1 initial + 2 retries). */
        const val MAX_ATTEMPTS = 3

        /** Base back-off between retries; the wait grows with each attempt. */
        const val RETRY_DELAY_MS = 2_000L
    }
}
