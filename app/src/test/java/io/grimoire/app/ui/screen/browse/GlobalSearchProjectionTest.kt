package io.grimoire.app.ui.screen.browse

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchProjectionTest {

    private fun novel(title: String) = Novel(url = "/$title", title = title, language = Language.EN)

    private fun result(
        name: String,
        novels: List<Novel> = emptyList(),
        loading: Boolean = false,
        error: String? = null,
    ) = GlobalSearchResult(
        sourceName = name,
        packageName = "pkg.$name",
        sourceId = name.hashCode().toLong(),
        novels = novels,
        isLoading = loading,
        error = error,
    )

    @Test
    fun `results first, then loading, then empty, then errors`() {
        val sorted = sortGlobalSearchResults(
            listOf(
                result("empty"),
                result("failed", error = "boom"),
                result("loading", loading = true),
                result("hits", novels = listOf(novel("Sword God"))),
            ),
            query = "sword",
        )
        assertEquals(listOf("hits", "loading", "empty", "failed"), sorted.map { it.sourceName })
    }

    @Test
    fun `within results, more query matches rank higher than more filler`() {
        val sorted = sortGlobalSearchResults(
            listOf(
                result("filler", novels = List(10) { novel("Unrelated $it") }),
                result("relevant", novels = listOf(novel("Sword God"), novel("Sword Saint"))),
            ),
            query = "sword",
        )
        assertEquals(listOf("relevant", "filler"), sorted.map { it.sourceName })
    }

    @Test
    fun `equal matches fall back to total count then name`() {
        val sorted = sortGlobalSearchResults(
            listOf(
                result("b", novels = listOf(novel("Sword 1"))),
                result("a", novels = listOf(novel("Sword 1"), novel("Other"))),
            ),
            query = "sword",
        )
        assertEquals(listOf("a", "b"), sorted.map { it.sourceName })
    }

    @Test
    fun `withResultsOnly drops loading, empty, and failed sources`() {
        val filtered = withResultsOnly(
            listOf(
                result("hits", novels = listOf(novel("x"))),
                result("loading", loading = true),
                result("empty"),
                result("failed", error = "boom"),
            ),
        )
        assertEquals(listOf("hits"), filtered.map { it.sourceName })
    }
}
