package io.grimoire.app.data.novelupdates

import java.net.URLEncoder

/**
 * Centralized NovelUpdates URLs. NU has no public API; everything is scraped, so
 * keeping every endpoint in one place makes a site change a one-file fix.
 */
object NovelUpdatesEndpoints {
    const val BASE_URL = "https://www.novelupdates.com"

    fun searchUrl(query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return "$BASE_URL/series-finder/?sf=1&sh=$q"
    }

    /**
     * Series Finder — the fullest search surface: free-text, sort, multi
     * language, included/excluded genres, AND/OR include gate. Returns the
     * `search_main_box_nu` cards [NovelUpdatesParser.parseListing] reads.
     */
    fun seriesFinderUrl(filter: NuBrowseFilter, page: Int): String {
        val sb = StringBuilder("$BASE_URL/series-finder/?sf=1")
        filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sb.append("&sh=").append(URLEncoder.encode(it, "UTF-8"))
        }
        appendCsv(sb, P_GENRE_INCLUDE, filter.genresInclude)
        appendCsv(sb, P_GENRE_EXCLUDE, filter.genresExclude)
        if (filter.genresInclude.isNotEmpty()) {
            sb.append("&$P_GENRE_GATE=").append(if (filter.genresMatchAll) "and" else "or")
        }
        appendCsv(sb, P_TAG_INCLUDE, filter.tagsInclude)
        appendCsv(sb, P_TAG_EXCLUDE, filter.tagsExclude)
        if (filter.tagsInclude.isNotEmpty()) {
            sb.append("&$P_TAG_GATE=").append(if (filter.tagsMatchAll) "and" else "or")
        }
        appendCsv(sb, P_LANGUAGE, filter.languages)
        appendCsv(sb, P_NOVEL_TYPE, filter.novelTypes)
        if (filter.storyStatus.value.isNotEmpty()) {
            sb.append("&$P_STORY_STATUS=").append(filter.storyStatus.value)
        }
        sb.append("&sort=").append(sortCode(filter.sort))
        sb.append("&order=").append(if (filter.orderAscending) "asc" else "desc")
        if (page > 1) sb.append("&pg=").append(page)
        return sb.toString()
    }

    /** The Series Finder page; embeds the full tag <select> (id + name). */
    fun filterFormUrl(): String = "$BASE_URL/series-finder/"

    /**
     * NovelUpdates' "Series Ranking" page. [type] is the Ranking Type select;
     * language/genre/AND-OR come from [filter]. Paginated with `&pg=`.
     */
    fun seriesRankingUrl(type: NuRankingType, filter: NuListingFilter, page: Int): String {
        val sb = StringBuilder("$BASE_URL/series-ranking/?rank=").append(rankCode(type))
        appendListingFilters(sb, filter)
        if (page > 1) sb.append("&pg=").append(page)
        return sb.toString()
    }

    /** NovelUpdates' "Latest Series" page, same listing filters as Ranking. */
    fun latestSeriesUrl(filter: NuListingFilter, page: Int): String {
        val sb = StringBuilder("$BASE_URL/latest-series/?st=1")
        appendListingFilters(sb, filter)
        if (page > 1) sb.append("&pg=").append(page)
        return sb.toString()
    }

    private fun appendListingFilters(sb: StringBuilder, filter: NuListingFilter) {
        appendCsv(sb, P_GENRE_INCLUDE, filter.genres)
        appendCsv(sb, P_LANGUAGE, filter.languages)
        if (filter.genres.isNotEmpty()) {
            sb.append("&$P_GENRE_GATE=").append(if (filter.genresMatchAll) "and" else "or")
        }
    }

    private fun appendCsv(sb: StringBuilder, param: String, values: List<String>) {
        if (values.isEmpty()) return
        sb.append("&").append(param).append("=")
            .append(URLEncoder.encode(values.joinToString(","), "UTF-8"))
    }

    // ---- NU param/value codes ----
    // Confirmed against real filtered URLs (series-finder + latest-series):
    // gi/ge/mgi/org and numeric genre/language ids. Centralized so any future
    // NU change stays a one-file fix.
    private const val P_GENRE_INCLUDE = "gi"
    private const val P_GENRE_EXCLUDE = "ge"
    private const val P_GENRE_GATE = "mgi"
    private const val P_LANGUAGE = "org"
    private const val P_NOVEL_TYPE = "nt"
    private const val P_STORY_STATUS = "ss"
    private const val P_TAG_INCLUDE = "tgi"
    private const val P_TAG_EXCLUDE = "tge"
    private const val P_TAG_GATE = "mtgi"

    private fun sortCode(sort: NuBrowseSort): String = sort.code

    // Series Ranking `rank=` codes — confirmed against real NU URLs.
    private fun rankCode(type: NuRankingType): String = when (type) {
        NuRankingType.POPULAR_MONTH -> "popmonth"
        NuRankingType.POPULAR_ALL -> "popular"
        NuRankingType.ACTIVITY_WEEK -> "week"
        NuRankingType.ACTIVITY_MONTH -> "month"
        NuRankingType.ACTIVITY_ALL -> "sixmonths"
    }

    fun seriesUrl(slugOrUrl: String): String {
        val s = slugOrUrl.trim()
        if (s.startsWith("http")) return s
        return "$BASE_URL/series/${s.trim('/')}/"
    }

    /** Extracts the `{slug}` from a `.../series/{slug}/` URL, or returns the input. */
    fun slugFromUrl(url: String): String {
        val marker = "/series/"
        val idx = url.indexOf(marker)
        if (idx < 0) return url.trim('/')
        return url.substring(idx + marker.length).trim('/').substringBefore('/')
    }
}
