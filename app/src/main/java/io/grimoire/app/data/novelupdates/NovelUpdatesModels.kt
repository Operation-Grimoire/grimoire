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

/** A single user review on a NovelUpdates series page. */
data class NuReview(
    val id: String,
    val author: String,
    val authorUrl: String? = null,
    val avatarUrl: String? = null,
    /** Filled stars, 0..5, or null when the reviewer left no score. */
    val rating: Int? = null,
    val date: String? = null,
    /** Reviewer's reading progress, e.g. "c200" (null when unspecified). */
    val progress: String? = null,
    val body: String,
    val likes: Int? = null,
    val permalink: String? = null,
)

/** Parsed metadata for one NovelUpdates series page. */
data class NuSeries(
    val slug: String,
    val url: String,
    val title: String,
    val type: String? = null,
    val language: String? = null,
    val authors: List<String> = emptyList(),
    val associatedNames: List<String> = emptyList(),
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val rating: Float? = null,
    val ratingVotes: Int? = null,
    val coverUrl: String? = null,
    val recommendations: List<NuRecommendation> = emptyList(),
    /** NovelUpdates internal series id (from review permalinks). */
    val sid: String? = null,
    val reviews: List<NuReview> = emptyList(),
    /** Number of review pages NU reports (1 = single page). */
    val reviewPageCount: Int = 1,
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

/**
 * Series Finder "Sort Results By" options (`&sort=` on `/series-finder/`).
 * Codes confirmed against the LNReader NovelUpdates plugin + a real URL.
 */
enum class NuBrowseSort(val code: String, val label: String) {
    READERS("sread", "Readers"),
    LAST_UPDATED("sdate", "Last updated"),
    RATING("srate", "Rating"),
    RANK("srank", "Rank"),
    REVIEWS("sreview", "Reviews"),
    CHAPTERS("srel", "Chapters"),
    FREQUENCY("sfrel", "Frequency"),
    TITLE("abc", "Title (A–Z)"),
}

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

/** Series Finder novel-type filter (`nt=`). Ids from the LNReader plugin. */
enum class NuNovelType(val id: String, val label: String) {
    LIGHT_NOVEL("2443", "Light Novel"),
    PUBLISHED_NOVEL("26874", "Published Novel"),
    WEB_NOVEL("2444", "Web Novel"),
}

/** Series Finder story-status filter (`ss=`). Values from the LNReader plugin. */
enum class NuStoryStatus(val value: String, val label: String) {
    ANY("", "Any"),
    COMPLETED("2", "Completed"),
    ONGOING("3", "Ongoing"),
    HIATUS("4", "Hiatus"),
}

/**
 * A NovelUpdates tag. [id] is the numeric term id Series Finder needs for
 * `tgi`/`tge`, taken from the `<select id="tags_include">` options embedded
 * in the live /series-finder/ page (loaded dynamically, never hardcoded).
 */
data class NuTag(val name: String, val id: String)

/**
 * The full Series Finder request: free-text [query], [sort]/[orderAscending],
 * multi-select languages, included/excluded genres + AND/OR gate, novel
 * types, story status, and included/excluded tags + their AND/OR gate.
 */
data class NuBrowseFilter(
    val query: String? = null,
    val sort: NuBrowseSort = NuBrowseSort.READERS,
    val orderAscending: Boolean = false,
    val languages: List<String> = emptyList(),
    val genresInclude: List<String> = emptyList(),
    val genresExclude: List<String> = emptyList(),
    val genresMatchAll: Boolean = false,
    val novelTypes: List<String> = emptyList(),
    val storyStatus: NuStoryStatus = NuStoryStatus.ANY,
    val tagsInclude: List<String> = emptyList(),
    val tagsExclude: List<String> = emptyList(),
    val tagsMatchAll: Boolean = false,
)

/** One page of a NovelUpdates listing plus whether another page follows. */
data class NuListingPage(
    val results: List<NuSearchResult>,
    val hasNext: Boolean,
)

/**
 * NovelUpdates genre taxonomy. Filters key on NU's numeric term IDs
 * (e.g. `gi=15,3`), NOT slugs. IDs taken verbatim from the authoritative
 * LNReader NovelUpdates plugin.
 */
object NuGenres {
    /** Display name -> NU numeric genre id. */
    val all: Map<String, String> = mapOf(
        "Action" to "8", "Adult" to "280", "Adventure" to "13",
        "Comedy" to "17", "Drama" to "9", "Ecchi" to "292",
        "Fantasy" to "5", "Gender Bender" to "168", "Harem" to "3",
        "Historical" to "330", "Horror" to "343", "Josei" to "324",
        "Martial Arts" to "14", "Mature" to "4", "Mecha" to "10",
        "Mystery" to "245", "Psychological" to "486", "Romance" to "15",
        "School Life" to "6", "Sci-fi" to "11", "Seinen" to "18",
        "Shoujo" to "157", "Shoujo Ai" to "851", "Shounen" to "12",
        "Shounen Ai" to "1692", "Slice of Life" to "7", "Smut" to "281",
        "Sports" to "1357", "Supernatural" to "16", "Tragedy" to "132",
        "Wuxia" to "479", "Xianxia" to "480", "Xuanhuan" to "3954",
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
