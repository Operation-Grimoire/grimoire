package io.grimoire.app.data.novelupdates

/** A single hit from a NovelUpdates site search. */
data class NuSearchResult(
    val title: String,
    val slug: String,
    val url: String,
    val coverUrl: String? = null,
    val rating: Float? = null,
    val language: String? = null,
    val stats: String? = null,
)

/** A NovelUpdates "recommendation" card shown on a series page. */
data class NuRecommendation(
    val title: String,
    val url: String,
    val coverUrl: String? = null,
)

/** Parsed metadata for one NovelUpdates series page. */
data class NuSeries(
    val slug: String,
    val url: String,
    val title: String,
    val associatedNames: List<String> = emptyList(),
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val rating: Float? = null,
    val ratingVotes: Int? = null,
    val coverUrl: String? = null,
    val recommendations: List<NuRecommendation> = emptyList(),
)

/**
 * Series Finder ordering. NovelUpdates exposes these via `&sort=` on
 * `/series-finder/`; the same endpoint backs every browse page so it reuses
 * the proven search-card parser.
 */
enum class NuBrowseSort { POPULAR, LATEST, RATING, TITLE, LAST_UPDATED, RANK }

/**
 * A NovelUpdates browse request. An empty/blank [query] drives a pure
 * filter/sort listing; a non-blank [query] is a Series Finder text search.
 */
data class NuBrowseFilter(
    val query: String? = null,
    val sort: NuBrowseSort = NuBrowseSort.POPULAR,
    val genreId: String? = null,
    val language: String? = null,
)

/** One page of a NovelUpdates listing plus whether another page follows. */
data class NuListingPage(
    val results: List<NuSearchResult>,
    val hasNext: Boolean,
)

/**
 * NovelUpdates' genre taxonomy. Stable enough to hardcode; the Series Finder
 * filters by the lowercase, hyphenated genre slug.
 */
object NuGenres {
    /** Display name -> Series Finder genre slug. */
    val all: Map<String, String> = listOf(
        "Action", "Adult", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
        "Gender Bender", "Harem", "Historical", "Horror", "Josei",
        "Martial Arts", "Mature", "Mecha", "Mystery", "Psychological",
        "Romance", "School Life", "Sci-fi", "Seinen", "Shoujo", "Shoujo Ai",
        "Shounen", "Shounen Ai", "Slice of Life", "Smut", "Sports",
        "Supernatural", "Tragedy", "Wuxia", "Xianxia", "Xuanhuan", "Yaoi",
        "Yuri",
    ).associateWith { it.lowercase().replace(' ', '-') }
}

/** NovelUpdates' origin-language filter values. */
object NuLanguages {
    val all: List<String> = listOf(
        "Chinese", "Filipino", "Indonesian", "Japanese", "Khmer", "Korean",
        "Malaysian", "Thai", "Vietnamese",
    )
}

/** UI state for the NovelUpdates section on the novel detail screen. */
sealed interface NuInfoState {
    data object Idle : NuInfoState
    data object Disabled : NuInfoState
    /** Enabled but not fetched yet — the section shows a "Load" button. */
    data object NotLoaded : NuInfoState
    data object Loading : NuInfoState
    data class Matched(val series: NuSeries) : NuInfoState
    data class Ambiguous(val candidates: List<NuSearchResult>) : NuInfoState
    data object NotFound : NuInfoState
    data class Error(val message: String) : NuInfoState
}
