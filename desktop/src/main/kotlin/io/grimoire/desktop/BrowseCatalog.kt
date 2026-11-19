package io.grimoire.desktop

import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.Source
import io.grimoire.api.source.feature.LatestSource
import io.grimoire.api.source.feature.PopularSource
import io.grimoire.api.source.feature.SearchSource

enum class BrowseMode(val label: String) { POPULAR("Popular"), LATEST("Latest"), SEARCH("Search") }

/** The catalog listings a source offers, in display order. */
internal fun browseModes(source: Source): List<BrowseMode> = listOfNotNull(
    BrowseMode.POPULAR.takeIf { source is PopularSource },
    BrowseMode.LATEST.takeIf { source is LatestSource },
    BrowseMode.SEARCH.takeIf { source is SearchSource },
)

/** One catalog page. Search runs without filters, like Android's global search. */
internal suspend fun catalogPage(source: Source, mode: BrowseMode, query: String, page: Int): List<Novel> = when (mode) {
    BrowseMode.POPULAR -> (source as? PopularSource ?: error("This source has no popular list")).getPopularNovels(page)
    BrowseMode.LATEST -> (source as? LatestSource ?: error("This source has no latest list")).getLatestUpdates(page)
    BrowseMode.SEARCH -> {
        require(query.isNotBlank()) { "Enter a title to search for" }
        (source as? SearchSource ?: error("This source cannot search")).searchNovels(query, page, emptyList())
    }
}

internal data class CatalogListing(val novels: List<Novel>, val hasMore: Boolean)

/**
 * Appends a page without duplicate URLs; a source may repeat titles within or across pages.
 * Paging stops when a page adds nothing new.
 */
internal fun mergeCatalog(existing: List<Novel>, page: List<Novel>): CatalogListing {
    val merged = (existing + page).distinctBy { it.url }
    return CatalogListing(merged, merged.size > existing.size)
}
