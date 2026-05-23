package io.grimoire.app.data.source

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fetches the full chapter list for [novel] from [src].
 *
 * For [PaginatedSource]s, pages are fetched in parallel windows of [window] so a
 * novel with many chapter-list pages no longer pays linear network latency. The
 * stop condition matches the previous sequential walker: collection ends at the
 * first empty page OR the first page that contributes no new URLs, and any
 * pages launched past that point in the same window are discarded.
 *
 * [onPageProgress] is invoked after each window with the highest page number
 * just fetched, so UI ("Loading page N…") can keep ticking.
 */
suspend fun fetchAllChapters(
    src: Source,
    novel: Novel,
    window: Int = 4,
    onPageProgress: (page: Int) -> Unit = {},
): List<Chapter> {
    if (src !is PaginatedSource) return src.getChapterList(novel)

    val all = mutableListOf<Chapter>()
    val seen = mutableSetOf<String>()
    var start = 1
    while (true) {
        val pages = (start until start + window).toList()
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
