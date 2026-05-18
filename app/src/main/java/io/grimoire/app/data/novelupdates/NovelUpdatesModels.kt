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
 * NovelUpdates' five Series Ranking types (the "Ranking Type" select on
 * `/series-ranking/`).
 */
enum class NuRankingType {
    POPULAR_MONTH, POPULAR_ALL, ACTIVITY_WEEK, ACTIVITY_MONTH, ACTIVITY_ALL;

    val label: String
        get() = when (this) {
            POPULAR_MONTH -> "Popular (Month)"
            POPULAR_ALL -> "Popular (All)"
            ACTIVITY_WEEK -> "Activity (Week)"
            ACTIVITY_MONTH -> "Activity (Month)"
            ACTIVITY_ALL -> "Activity (All)"
        }
}

/** Series Finder ordering (`&sort=` on `/series-finder/`). */
enum class NuBrowseSort { POPULAR, LATEST, RATING, TITLE, LAST_UPDATED, RANK }

/**
 * Filters shared by the Rankings and Latest listing pages: multi-select
 * languages, multi-select genres, and whether genres are matched with AND
 * (all) or OR (any).
 */
data class NuListingFilter(
    val languages: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val genresMatchAll: Boolean = false,
)

/**
 * The fuller Series Finder request: free-text [query], a [sort], multi-select
 * languages, included/excluded genres, and the include AND/OR gate.
 */
data class NuBrowseFilter(
    val query: String? = null,
    val sort: NuBrowseSort = NuBrowseSort.POPULAR,
    val languages: List<String> = emptyList(),
    val genresInclude: List<String> = emptyList(),
    val genresExclude: List<String> = emptyList(),
    val genresMatchAll: Boolean = false,
)

/** One page of a NovelUpdates listing plus whether another page follows. */
data class NuListingPage(
    val results: List<NuSearchResult>,
    val hasNext: Boolean,
)

/**
 * NovelUpdates genre taxonomy. The Series Finder / listing filters key on
 * NU's numeric term IDs (e.g. `gi=13,5,10`), NOT slugs. These IDs are stable
 * WordPress term IDs; verified against a real filtered URL (Comedy=17,
 * Fantasy=5, Mystery=13, Harem=10, Ecchi=292).
 */
object NuGenres {
    /** Display name -> NU numeric genre id. */
    val all: Map<String, String> = mapOf(
        "Action" to "8", "Adult" to "280", "Adventure" to "9",
        "Comedy" to "17", "Drama" to "178", "Ecchi" to "292",
        "Fantasy" to "5", "Gender Bender" to "905", "Harem" to "10",
        "Historical" to "330", "Horror" to "343", "Josei" to "324",
        "Martial Arts" to "14", "Mature" to "297", "Mecha" to "921",
        "Mystery" to "13", "Psychological" to "15", "Romance" to "4",
        "School Life" to "331", "Sci-fi" to "11", "Seinen" to "325",
        "Shoujo" to "326", "Shoujo Ai" to "327", "Shounen" to "328",
        "Shounen Ai" to "329", "Slice of Life" to "20", "Smut" to "305",
        "Sports" to "308", "Supernatural" to "6", "Tragedy" to "24",
        "Wuxia" to "479", "Xianxia" to "480", "Xuanhuan" to "3239",
        "Yaoi" to "560", "Yuri" to "922",
    )
}

/**
 * NovelUpdates origin-language filter. Keyed by NU numeric ids (`org=`);
 * derived from a real filtered URL where the 9 languages mapped in display
 * order (Chinese=495, Japanese=496, Korean=497 are the sequential originals).
 */
object NuLanguages {
    /** Display name -> NU numeric language id. */
    val all: Map<String, String> = mapOf(
        "Chinese" to "495", "Filipino" to "9181", "Indonesian" to "9179",
        "Japanese" to "496", "Khmer" to "18657", "Korean" to "497",
        "Malaysian" to "9183", "Thai" to "9954", "Vietnamese" to "9177",
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
