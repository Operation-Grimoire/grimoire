package io.grimoire.app.data.source

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
 * Fetches the full chapter list for [novel] from [src].
 *
 * For [PaginatedSource]s, pages are fetched in parallel windows of [window] so a
 * novel with many chapter-list pages no longer pays linear network latency. The
 * stop condition matches the previous sequential walker: collection ends at the
 * first empty page OR the first page that contributes no new URLs, and any
 * pages launched past that point in the same window are discarded. As a safety
 * net the walk also stops after [maxPages] pages so a source with broken
 * pagination cannot exhaust memory.
 *
 * [onPageProgress] is invoked after each window with the highest page number
 * just fetched, so UI ("Loading page N…") can keep ticking.
 */
suspend fun fetchAllChapters(
    src: Source,
    novel: Novel,
    window: Int = 4,
    maxPages: Int = MAX_CHAPTER_PAGES,
    onPageProgress: (page: Int) -> Unit = {},
): List<Chapter> {
    if (src !is PaginatedSource) return src.getChapterList(novel)

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
        for (batch in batches) {
            if (batch.isEmpty()) {
                stop = true
                break
            }
            val new = batch.filter { seen.add(it.url) }
            if (new.isEmpty()) {
                stop = true
                break
            }
            all += new
        }
        if (stop) break
        start += window
    }
    return all
}
