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
     * Series Finder used as a browse/filter listing. A blank [query] yields a
     * pure sort+filter listing; a non-blank one is a text search. Returns the
     * same `search_main_box_nu` cards [NovelUpdatesParser.parseListing] reads.
     *
     * Param codes are best-effort and centralized here on purpose: NU has no
     * API and changes occasionally, so a breakage is a one-file fix. Verify
     * against the live site (only the app's Cloudflare-passing client can
     * reach it) and adjust the constants below if NU changes them.
     */
    fun seriesFinderUrl(
        query: String?,
        page: Int,
        sort: NuBrowseSort,
        genreSlug: String?,
        language: String?,
    ): String {
        val sb = StringBuilder("$BASE_URL/series-finder/?sf=1")
        query?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sb.append("&sh=").append(URLEncoder.encode(it, "UTF-8"))
        }
        genreSlug?.takeIf { it.isNotBlank() }?.let {
            sb.append("&gr=").append(URLEncoder.encode(it, "UTF-8"))
        }
        language?.takeIf { it.isNotBlank() }?.let {
            sb.append("&org=").append(URLEncoder.encode(it.lowercase(), "UTF-8"))
        }
        sb.append("&sort=").append(sortCode(sort))
        sb.append("&order=").append(if (sort == NuBrowseSort.TITLE) "asc" else "desc")
        if (page > 1) sb.append("&pg=").append(page)
        return sb.toString()
    }

    /** Series-ranking "leaderboard". Distinct DOM — see parseRanking. */
    fun seriesRankingUrl(window: NuRankWindow, page: Int): String {
        val rank = when (window) {
            NuRankWindow.WEEK -> "week"
            NuRankWindow.MONTH -> "month"
            NuRankWindow.ALL -> "alltime"
        }
        val base = "$BASE_URL/series-ranking/?rank=$rank"
        return if (page > 1) "$base&pg=$page" else base
    }

    private fun sortCode(sort: NuBrowseSort): String = when (sort) {
        // Confirmed: sdate = date added. Others follow NU's documented codes.
        NuBrowseSort.LATEST -> "sdate"
        NuBrowseSort.POPULAR -> "sread"
        NuBrowseSort.RATING -> "sfrating"
        NuBrowseSort.TITLE -> "abc"
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
