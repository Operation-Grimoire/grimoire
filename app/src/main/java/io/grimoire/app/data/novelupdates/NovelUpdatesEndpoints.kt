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
        return "$BASE_URL/?s=$q&post_type=seriesplans"
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
