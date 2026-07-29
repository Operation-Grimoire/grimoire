package io.grimoire.app.data.source

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.web.ChapterListSource
import io.grimoire.api.source.web.PaginatedSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ChapterListFetcherTest {

    @Test
    fun fetchAllChapters_paginatedSource_returnsAllPagesInOrder() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 7, perPageDelayMs = 0)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        assertEquals(7 * 3, result.size)
        // Verify ordering: page 1 chapters come before page 2 chapters, etc.
        result.forEachIndexed { idx, ch ->
            val expectedPage = idx / 3 + 1
            assertTrue(ch.url.startsWith("p$expectedPage-"))
        }
    }

    @Test
    fun fetchAllChapters_paginatedSource_dedupesByUrl() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 3, perPageDelayMs = 0, duplicateAcross = true)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        // duplicateAcross means each page returns the same 3 URLs.
        // Page 1 contributes 3; pages 2+ contribute 0 (all dupes) → stop at page 2.
        assertEquals(3, result.size)
    }

    @Test
    fun fetchAllChapters_paginatedSource_runsPagesInParallel() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 8, perPageDelayMs = 100)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val start = System.currentTimeMillis()
        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(8 * 3, result.size)
        // Sequential would be 8 * 100ms = 800ms. With window=4 we run two windows of 4
        // plus one window that detects the empty page, so ~300ms. Generous 600ms bound
        // tolerates CI jitter while still catching a regression to sequential behavior.
        assertTrue("expected parallel speedup, took ${elapsed}ms", elapsed < 600)
    }

    @Test
    fun fetchAllChapters_paginatedSource_stopsAtMaxPages() = runBlocking {
        // A source that never returns an empty page and never repeats a URL (e.g.
        // pagination broken by a site change) would loop forever and OOM. The
        // maxPages safety net must bound the walk.
        val src = FakePaginatedSource(totalNonEmptyPages = Int.MAX_VALUE, perPageDelayMs = 0)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, maxPages = 10, retryDelayMs = 0)

        // 10 pages walked, 3 chapters each, then the cap stops the walk.
        assertEquals(10 * 3, result.size)
        assertTrue("walk should stop near the cap", src.calls.get() <= 12)
    }

    @Test
    fun fetchAllChapters_nonPaginatedSource_callsSimpleApi() = runBlocking {
        val chapters = listOf(chapter("a"), chapter("b"))
        val src = FakeSimpleSource(chapters)

        val result = fetchAllChapters(src, Novel(url = "n", title = "", language = Language.UNKNOWN))

        assertEquals(chapters, result)
    }

    @Test
    fun fetchAllChapters_paginatedSource_reportsPageProgress() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 5, perPageDelayMs = 0)
        val seen = mutableListOf<Int>()

        fetchAllChapters(src, Novel(url = "n", title = "", language = Language.UNKNOWN), window = 4, retryDelayMs = 0) { seen += it }

        // First window reports page 4 (highest in [1,4]); second window reports page 8.
        assertEquals(listOf(4, 8), seen)
    }

    @Test
    fun fetchAllChapters_transientEmptyPage_recoversViaRetry() = runBlocking {
        // Page 2 fails (empty) exactly once; the in-place retry must heal it and
        // the final list must be complete and in order.
        val src = FakePaginatedSource(totalNonEmptyPages = 7, perPageDelayMs = 0, failOncePages = setOf(2))
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        assertEquals(7 * 3, result.size)
        result.forEachIndexed { idx, ch ->
            val expectedPage = idx / 3 + 1
            assertTrue(ch.url.startsWith("p$expectedPage-"))
        }
    }

    @Test
    fun fetchAllChapters_persistentEmptyMidList_throwsInsteadOfTruncating() = runBlocking {
        // Page 2 is empty on every attempt while page 3 still has chapters — the
        // old behavior silently returned pages 1..1; now it must fail loudly so
        // the caller keeps the existing chapter list.
        val src = FakePaginatedSource(totalNonEmptyPages = 7, perPageDelayMs = 0, alwaysEmptyPages = setOf(2))
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val thrown = runCatching { fetchAllChapters(src, novel, window = 4, retryDelayMs = 0) }
        assertTrue(thrown.exceptionOrNull() is TruncatedChapterListException)
    }

    @Test
    fun fetchAllChapters_trailingEmptyPage_stillStopsNormally() = runBlocking {
        // The empty page after the real end of the list is retried once and then
        // accepted as the end — no exception, complete list.
        val src = FakePaginatedSource(totalNonEmptyPages = 4, perPageDelayMs = 0)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        assertEquals(4 * 3, result.size)
    }

    @Test
    fun fetchAllChapters_declaredPageCount_fetchesExactlyThatRange() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 7, perPageDelayMs = 0, pageCount = 7)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        assertEquals(7 * 3, result.size)
        // No trailing empty-page probe: exactly the declared pages are fetched.
        assertEquals(7, src.calls.get())
    }

    @Test
    fun fetchAllChapters_declaredPageCount_emptyFinalPageThrows() = runBlocking {
        // The heuristic walk would read an empty last page as a normal stop; a
        // declared count makes it a provable failure.
        val src = FakePaginatedSource(totalNonEmptyPages = 6, perPageDelayMs = 0, pageCount = 7)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val thrown = runCatching { fetchAllChapters(src, novel, window = 4, retryDelayMs = 0) }
        assertTrue(thrown.exceptionOrNull() is TruncatedChapterListException)
    }

    @Test
    fun fetchAllChapters_declaredPageCount_transientEmptyRecovers() = runBlocking {
        val src = FakePaginatedSource(
            totalNonEmptyPages = 7, perPageDelayMs = 0, pageCount = 7, failOncePages = setOf(3),
        )
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        assertEquals(7 * 3, result.size)
    }

    @Test
    fun fetchAllChapters_brokenGetPageCount_fallsBackToHeuristic() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 5, perPageDelayMs = 0, pageCountThrows = true)
        val novel = Novel(url = "n", title = "", language = Language.UNKNOWN)

        val result = fetchAllChapters(src, novel, window = 4, retryDelayMs = 0)

        assertEquals(5 * 3, result.size)
    }

    private fun chapter(suffix: String) =
        Chapter(url = suffix, name = "Chapter $suffix")

    private class FakePaginatedSource(
        private val totalNonEmptyPages: Int,
        private val perPageDelayMs: Long,
        private val duplicateAcross: Boolean = false,
        /** Pages that return empty on their first call only (transient failure). */
        private val failOncePages: Set<Int> = emptySet(),
        /** Pages that return empty on every call (persistent failure). */
        private val alwaysEmptyPages: Set<Int> = emptySet(),
        /** Declared page count for the strict path; null = heuristic walk. */
        private val pageCount: Int? = null,
        private val pageCountThrows: Boolean = false,
    ) : PaginatedSource {
        override val name: String = "fake"
        override val lang: Language = Language.EN
        val calls = AtomicInteger(0)
        private val failedOnce = mutableSetOf<Int>()

        override suspend fun getNovelDetails(novel: Novel): Novel = novel

        override suspend fun getPageCount(novel: Novel): Int? {
            if (pageCountThrows) error("page count unavailable")
            return pageCount
        }

        override suspend fun getChapterList(novel: Novel, page: Int): List<Chapter> {
            calls.incrementAndGet()
            if (perPageDelayMs > 0) delay(perPageDelayMs)
            if (page in alwaysEmptyPages) return emptyList()
            if (page in failOncePages && failedOnce.add(page)) return emptyList()
            if (page > totalNonEmptyPages) return emptyList()
            val prefix = if (duplicateAcross) "p1" else "p$page"
            return listOf(
                Chapter(url = "$prefix-a", name = "$prefix-a"),
                Chapter(url = "$prefix-b", name = "$prefix-b"),
                Chapter(url = "$prefix-c", name = "$prefix-c"),
            )
        }
    }

    private class FakeSimpleSource(private val chapters: List<Chapter>) : ChapterListSource {
        override val name: String = "simple"
        override val lang: Language = Language.EN
        override suspend fun getNovelDetails(novel: Novel): Novel = novel
        override suspend fun getChapterList(novel: Novel): List<Chapter> = chapters
    }
}
