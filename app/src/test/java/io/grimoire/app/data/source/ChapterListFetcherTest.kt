package io.grimoire.app.data.source

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelPage
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
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
        val novel = Novel(url = "n", title = "")

        val result = fetchAllChapters(src, novel, window = 4)

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
        val novel = Novel(url = "n", title = "")

        val result = fetchAllChapters(src, novel, window = 4)

        // duplicateAcross means each page returns the same 3 URLs.
        // Page 1 contributes 3; pages 2+ contribute 0 (all dupes) → stop at page 2.
        assertEquals(3, result.size)
    }

    @Test
    fun fetchAllChapters_paginatedSource_runsPagesInParallel() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 8, perPageDelayMs = 100)
        val novel = Novel(url = "n", title = "")

        val start = System.currentTimeMillis()
        val result = fetchAllChapters(src, novel, window = 4)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(8 * 3, result.size)
        // Sequential would be 8 * 100ms = 800ms. With window=4 we run two windows of 4
        // plus one window that detects the empty page, so ~300ms. Generous 600ms bound
        // tolerates CI jitter while still catching a regression to sequential behavior.
        assertTrue("expected parallel speedup, took ${elapsed}ms", elapsed < 600)
    }

    @Test
    fun fetchAllChapters_nonPaginatedSource_callsSimpleApi() = runBlocking {
        val chapters = listOf(chapter("a"), chapter("b"))
        val src = FakeSimpleSource(chapters)

        val result = fetchAllChapters(src, Novel(url = "n", title = ""))

        assertEquals(chapters, result)
    }

    @Test
    fun fetchAllChapters_paginatedSource_reportsPageProgress() = runBlocking {
        val src = FakePaginatedSource(totalNonEmptyPages = 5, perPageDelayMs = 0)
        val seen = mutableListOf<Int>()

        fetchAllChapters(src, Novel(url = "n", title = ""), window = 4) { seen += it }

        // First window reports page 4 (highest in [1,4]); second window reports page 8.
        assertEquals(listOf(4, 8), seen)
    }

    private fun chapter(suffix: String) =
        Chapter(url = suffix, name = "Chapter $suffix")

    private class FakePaginatedSource(
        private val totalNonEmptyPages: Int,
        private val perPageDelayMs: Long,
        private val duplicateAcross: Boolean = false,
    ) : PaginatedSource {
        override val id: Long = 1L
        override val name: String = "fake"
        override val lang: String = "en"
        val calls = AtomicInteger(0)

        override suspend fun getNovelDetails(novel: Novel): Novel = novel
        override suspend fun getChapterList(novel: Novel): List<Chapter> = emptyList()
        override suspend fun getPageList(chapter: Chapter): List<NovelPage> = emptyList()

        override suspend fun getChapterList(novel: Novel, page: Int): List<Chapter> {
            calls.incrementAndGet()
            if (perPageDelayMs > 0) delay(perPageDelayMs)
            if (page > totalNonEmptyPages) return emptyList()
            val prefix = if (duplicateAcross) "p1" else "p$page"
            return listOf(
                Chapter(url = "$prefix-a", name = "$prefix-a"),
                Chapter(url = "$prefix-b", name = "$prefix-b"),
                Chapter(url = "$prefix-c", name = "$prefix-c"),
            )
        }
    }

    private class FakeSimpleSource(private val chapters: List<Chapter>) : Source {
        override val id: Long = 2L
        override val name: String = "simple"
        override val lang: String = "en"
        override suspend fun getNovelDetails(novel: Novel): Novel = novel
        override suspend fun getChapterList(novel: Novel): List<Chapter> = chapters
        override suspend fun getPageList(chapter: Chapter): List<NovelPage> = emptyList()
    }
}
