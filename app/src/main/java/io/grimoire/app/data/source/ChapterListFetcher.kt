package io.grimoire.app.data.source

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.web.ChapterListSource
import io.grimoire.api.source.web.PaginatedSource
import io.grimoire.api.source.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Hard ceiling on how many chapter-list pages [fetchAllChapters] will walk for a
 * single paginated novel.
 *
 * The page walk normally stops at the first empty page or the first page that
 * contributes no new URLs. A source whose pagination misbehaves — a site change
 * that breaks the empty/duplicate stop heuristic, or chapter URLs that carry a
 * volatile token so every page looks "new" — would otherwise loop forever,
 * accumulating [Chapter] objects until the process runs out of memory (the
 * library-sync OOM that crashed the whole app on open). No legitimate novel has
 * anywhere near this many pages, so capping the walk here bounds memory without
 * truncating real content.
 */
const val MAX_CHAPTER_PAGES = 1_000

/**
 * Thrown when a chapter-list page came back empty (or contributed nothing new)
 * while a later page in the same window still had chapters — a transient source
 * failure (throttle page, hiccup) that would otherwise silently truncate the
 * list. Callers treat it like any other fetch error: the novel's existing
 * chapters are left untouched.
 */
class TruncatedChapterListException(page: Int) : Exception(
    "Chapter list page $page returned no chapters but a later page has content — transient source failure",
)

/**
 * Fetches the full chapter list for [novel] from [src].
 *
 * For [PaginatedSource]s, pages are fetched in parallel windows of [window] so a
 * novel with many chapter-list pages no longer pays linear network latency.
 * Collection ends at the first empty page OR the first page that contributes no
 * new URLs — but only after that page has been **retried once** (a throttled
 * site's error page parses as an empty list, and believing it truncates the
 * list). If the retried page is still empty while a later page in the window
 * has content, the fetch fails with [TruncatedChapterListException] instead of
 * returning a silently shortened list. As a safety net the walk also stops
 * after [maxPages] pages so a source with broken pagination cannot exhaust
 * memory.
 *
 * [onPageProgress] is invoked after each window with the highest page number
 * just fetched, so UI ("Loading page N…") can keep ticking.
 */
suspend fun fetchAllChapters(
    src: Source,
    novel: Novel,
    window: Int = 4,
    maxPages: Int = MAX_CHAPTER_PAGES,
    retryDelayMs: Long = PAGE_RETRY_DELAY_MS,
    onPageProgress: (page: Int) -> Unit = {},
): List<Chapter> {
    if (src !is PaginatedSource) {
        return (src as? ChapterListSource)?.getChapterList(novel) ?: emptyList()
    }

    val all = mutableListOf<Chapter>()
    val seen = mutableSetOf<String>()
    var start = 1
    while (start <= maxPages) {
        // Clamp the window so we never fetch past the safety ceiling.
        val pages = (start..minOf(start + window - 1, maxPages)).toList()
        val batches = coroutineScope {
            pages.map { p -> async { src.getChapterList(novel, p) } }.awaitAll()
        }
        onPageProgress(pages.last())
        var stop = false
        for (i in batches.indices) {
            var fresh = batches[i].filter { it.url !in seen }.distinctBy { it.url }
            if (fresh.isEmpty()) {
                if (retryDelayMs > 0) delay(retryDelayMs)
                fresh = src.getChapterList(novel, pages[i])
                    .filter { it.url !in seen }
                    .distinctBy { it.url }
            }
            if (fresh.isEmpty()) {
                val laterHasContent = (i + 1 until batches.size).any { j ->
                    batches[j].any { it.url !in seen }
                }
                if (laterHasContent) throw TruncatedChapterListException(pages[i])
                stop = true
                break
            }
            fresh.forEach { seen.add(it.url) }
            all += fresh
        }
        if (stop) break
        start += window
    }
    return all
}

/** Pause before retrying an empty page, giving a throttling site a beat to recover. */
const val PAGE_RETRY_DELAY_MS = 1_000L
