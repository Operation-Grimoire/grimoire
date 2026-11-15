package io.grimoire.app.ui.screen.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceBrowseEmptyStateTest {

    @Test
    fun `shows loading while reset fetch is in flight`() {
        val state = resolveSourceBrowseContent(
            isLoading = true,
            cloudflareBlocked = false,
            error = null,
            mode = BrowseMode.POPULAR,
            query = "",
            hasActiveFilters = false,
            hasLoaded = false,
            hasNovels = false,
        )
        assertEquals(SourceBrowseContent.LOADING, state)
    }

    @Test
    fun `before initial fetch completes does not flash no results`() {
        val state = resolveSourceBrowseContent(
            isLoading = false,
            cloudflareBlocked = false,
            error = null,
            mode = BrowseMode.POPULAR,
            query = "",
            hasActiveFilters = false,
            hasLoaded = false,
            hasNovels = false,
        )
        assertEquals(SourceBrowseContent.RESULTS, state)
    }

    @Test
    fun `completed empty fetch in popular or latest shows no results`() {
        for (mode in listOf(BrowseMode.POPULAR, BrowseMode.LATEST)) {
            val state = resolveSourceBrowseContent(
                isLoading = false,
                cloudflareBlocked = false,
                error = null,
                mode = mode,
                query = "",
                hasActiveFilters = false,
                hasLoaded = true,
                hasNovels = false,
            )
            assertEquals(SourceBrowseContent.EMPTY_RESULTS, state)
        }
    }

    @Test
    fun `completed empty search shows no results, while blank unsubmitted search prompts to type`() {
        assertEquals(
            SourceBrowseContent.EMPTY_RESULTS,
            resolveSourceBrowseContent(
                isLoading = false,
                cloudflareBlocked = false,
                error = null,
                mode = BrowseMode.SEARCH,
                query = "nonexistent",
                hasActiveFilters = false,
                hasLoaded = true,
                hasNovels = false,
            ),
        )
        assertEquals(
            SourceBrowseContent.SEARCH_PROMPT,
            resolveSourceBrowseContent(
                isLoading = false,
                cloudflareBlocked = false,
                error = null,
                mode = BrowseMode.SEARCH,
                query = "   ",
                hasActiveFilters = false,
                hasLoaded = false,
                hasNovels = false,
            ),
        )
    }

    @Test
    fun `cloudflare and error states take precedence over empty results`() {
        assertEquals(
            SourceBrowseContent.CLOUDFLARE_BLOCKED,
            resolveSourceBrowseContent(
                isLoading = false,
                cloudflareBlocked = true,
                error = null,
                mode = BrowseMode.POPULAR,
                query = "",
                hasActiveFilters = false,
                hasLoaded = true,
                hasNovels = false,
            ),
        )
        assertEquals(
            SourceBrowseContent.ERROR,
            resolveSourceBrowseContent(
                isLoading = false,
                cloudflareBlocked = false,
                error = "HTTP 500",
                mode = BrowseMode.POPULAR,
                query = "",
                hasActiveFilters = false,
                hasLoaded = true,
                hasNovels = false,
            ),
        )
    }
}
