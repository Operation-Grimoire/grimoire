package io.grimoire.app.ui.screen.browse

/**
 * Orders global-search source groups so the useful ones surface first:
 * sources with results (most relevant on top), then the ones still loading,
 * then empty sources, and failures last. Relevance within the results bucket
 * is how many titles actually contain the query (a source can answer a search
 * with unrelated "popular" filler), with total count as the tiebreaker.
 */
internal fun sortGlobalSearchResults(
    results: List<GlobalSearchResult>,
    query: String,
): List<GlobalSearchResult> {
    val q = query.trim()
    fun bucket(r: GlobalSearchResult): Int = when {
        r.novels.isNotEmpty() -> 0
        r.isLoading -> 1
        r.error == null -> 2
        else -> 3
    }

    fun matchCount(r: GlobalSearchResult): Int =
        if (q.isEmpty()) 0 else r.novels.count { it.title.contains(q, ignoreCase = true) }

    return results.sortedWith(
        compareBy<GlobalSearchResult> { bucket(it) }
            .thenByDescending { matchCount(it) }
            .thenByDescending { it.novels.size }
            .thenBy { it.sourceName.lowercase() },
    )
}

/** The "with results" tab: only sources that actually returned novels. */
internal fun withResultsOnly(results: List<GlobalSearchResult>): List<GlobalSearchResult> =
    results.filter { it.novels.isNotEmpty() }
