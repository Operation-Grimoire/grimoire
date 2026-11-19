package io.grimoire.desktop

import io.grimoire.api.model.filter.Filter
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.Source
import io.grimoire.api.source.feature.LatestSource
import io.grimoire.api.source.feature.SearchSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseCatalogTest {
    private fun novel(url: String) = Novel(url, url.removePrefix("/"), Language.EN)

    private class SearchOnly : SearchSource, LatestSource {
        override val name = "Search only"
        override val lang = Language.EN
        val searches = mutableListOf<Triple<String, Int, List<Filter<*>>>>()
        override suspend fun getNovelDetails(novel: Novel) = novel
        override suspend fun searchNovels(query: String, page: Int, filters: List<Filter<*>>): List<Novel> {
            searches += Triple(query, page, filters)
            return listOf(Novel("/$query-$page", query, Language.EN))
        }
        override suspend fun getLatestUpdates(page: Int) = listOf(Novel("/latest-$page", "Latest", Language.EN))
    }

    @Test fun modesFollowTheSourceCapabilities() {
        assertEquals(listOf(BrowseMode.LATEST, BrowseMode.SEARCH), browseModes(SearchOnly()))
        val bare = object : Source {
            override val name = "Bare"
            override val lang = Language.EN
            override suspend fun getNovelDetails(novel: Novel) = novel
        }
        assertTrue(browseModes(bare).isEmpty())
    }

    @Test fun pagesDispatchToTheMatchingCapability() = runBlocking {
        val source = SearchOnly()
        assertEquals("/latest-2", catalogPage(source, BrowseMode.LATEST, "", 2).single().url)
        assertEquals("/moon-3", catalogPage(source, BrowseMode.SEARCH, "moon", 3).single().url)
        assertEquals(listOf(Triple("moon", 3, emptyList<Filter<*>>())), source.searches)
        val unsupported = runCatching { catalogPage(source, BrowseMode.POPULAR, "", 1) }.exceptionOrNull()
        assertEquals("This source has no popular list", unsupported?.message)
        val blank = runCatching { catalogPage(source, BrowseMode.SEARCH, " ", 1) }.exceptionOrNull()
        assertEquals("Enter a title to search for", blank?.message)
        assertTrue(source.searches.size == 1)
    }

    @Test fun mergingDropsRepeatedTitlesAndStopsWhenNothingIsNew() {
        val first = mergeCatalog(emptyList(), listOf(novel("/a"), novel("/a"), novel("/b")))
        assertEquals(listOf("/a", "/b"), first.novels.map { it.url })
        assertTrue(first.hasMore)
        val repeated = mergeCatalog(first.novels, listOf(novel("/b"), novel("/a")))
        assertEquals(first.novels, repeated.novels)
        assertFalse(repeated.hasMore)
        assertFalse(mergeCatalog(emptyList(), emptyList()).hasMore)
    }
}
