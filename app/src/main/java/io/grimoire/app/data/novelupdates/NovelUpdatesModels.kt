package io.grimoire.app.data.novelupdates

/** A single hit from a NovelUpdates site search. */
data class NuSearchResult(
    val title: String,
    val slug: String,
    val url: String,
    val coverUrl: String? = null,
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

/** UI state for the NovelUpdates section on the novel detail screen. */
sealed interface NuInfoState {
    data object Idle : NuInfoState
    data object Disabled : NuInfoState
    data object Loading : NuInfoState
    data class Matched(val series: NuSeries) : NuInfoState
    data class Ambiguous(val candidates: List<NuSearchResult>) : NuInfoState
    data object NotFound : NuInfoState
    data class Error(val message: String) : NuInfoState
}
