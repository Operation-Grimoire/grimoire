package io.grimoire.app.data.novelupdates

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Pure Jsoup parsing for NovelUpdates pages. No network here so it stays
 * unit-testable against saved HTML fixtures.
 *
 * Every CSS selector is a `const` in one place: NovelUpdates has no API and its
 * DOM changes occasionally, so a breakage is fixed by editing only this file.
 * Selectors are best-effort and may need updating against the live site.
 */
object NovelUpdatesParser {

    // --- Search results ---
    // NU serves a different DOM to mobile UAs (the app sends an Android UA):
    // desktop nests .search_title under .search_body_nu, mobile puts it
    // directly under .search_main_box_nu. Match it regardless of parent, and
    // restrict to the actual series link.
    private const val SEARCH_RESULT = "div.search_main_box_nu"
    private const val SEARCH_TITLE_LINK = "div.search_title a[href*=/series/]"
    private const val SEARCH_IMG = "div.search_img_nu img"
    private const val SEARCH_RATING = "span.search_ratings"
    private const val SEARCH_LANG = "span[class^=org]"
    private const val SEARCH_STATS = "div.search_stats"

    private const val CDN_COVER = "https://cdn.novelupdates.com/imgmid/series_%s.jpg"
    private val SID = Regex("""sid(\d+)""")

    // --- Series page ---
    private const val SERIES_TITLE = "div.seriestitlenu"
    private const val SERIES_COVER = "div.seriesimg img"
    private const val SERIES_ASSOCIATED = "#editassociated"
    private const val SERIES_DESCRIPTION = "#editdescription"
    private const val SERIES_GENRES = "#seriesgenre a"
    private const val SERIES_TAGS = "#showtags a"
    private const val SERIES_STATUS = "#editstatus"
    private const val SERIES_RATING = "span.uvotes"

    private inline fun <T> safe(default: T, block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            default
        }

    fun parseSearch(doc: Document): List<NuSearchResult> = safe(emptyList()) {
        doc.select(SEARCH_RESULT).mapNotNull { box ->
            val link = box.selectFirst(SEARCH_TITLE_LINK) ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            if (href.isBlank()) return@mapNotNull null
            val title = link.text().trim()
            if (title.isBlank()) return@mapNotNull null
            NuSearchResult(
                title = title,
                slug = NovelUpdatesEndpoints.slugFromUrl(href),
                url = href,
                coverUrl = box.selectFirst(SEARCH_IMG)?.imgSrc(),
                rating = box.selectFirst(SEARCH_RATING)?.text()
                    ?.let { Regex("""[0-9]+(?:\.[0-9]+)?""").find(it)?.value?.toFloatOrNull() },
                language = box.selectFirst(SEARCH_LANG)?.text()?.trim()?.ifBlank { null },
                stats = box.selectFirst(SEARCH_STATS)?.text()
                    ?.replace(Regex("""\s+"""), " ")?.trim()?.ifBlank { null },
            )
        }
    }

    fun parseSeries(doc: Document, requestUrl: String): NuSeries {
        val slug = NovelUpdatesEndpoints.slugFromUrl(requestUrl)
        val title = doc.selectFirst(SERIES_TITLE)?.text()?.trim().orEmpty()
        return NuSeries(
            slug = slug,
            url = requestUrl,
            title = title,
            associatedNames = parseAssociated(doc),
            description = doc.selectFirst(SERIES_DESCRIPTION)?.text()?.trim()?.ifBlank { null },
            genres = doc.select(SERIES_GENRES).map { it.text().trim() }.filter { it.isNotBlank() }.distinct(),
            tags = doc.select(SERIES_TAGS).map { it.text().trim() }.filter { it.isNotBlank() }.distinct(),
            status = doc.selectFirst(SERIES_STATUS)?.text()?.trim()?.ifBlank { null },
            rating = parseRating(doc),
            ratingVotes = parseRatingVotes(doc),
            coverUrl = doc.selectFirst(SERIES_COVER)?.imgSrc(),
            recommendations = parseRecommendations(doc),
        )
    }

    private fun parseAssociated(doc: Document): List<String> = safe(emptyList()) {
        val el = doc.selectFirst(SERIES_ASSOCIATED) ?: return@safe emptyList()
        el.html()
            .split("<br>", "<br/>", "<br />", ignoreCase = true)
            .map { org.jsoup.Jsoup.parse(it).text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    // NU renders the rating as e.g. "3.9 / 5.0" inside span.uvotes; votes appear
    // nearby as "(123 votes)". Both are best-effort.
    private fun parseRating(doc: Document): Float? = safe(null) {
        val text = doc.selectFirst(SERIES_RATING)?.text()
        if (text == null) {
            null
        } else {
            Regex("""([0-9]+(?:\.[0-9]+)?)\s*/\s*5""")
                .find(text)?.groupValues?.get(1)?.toFloatOrNull()
        }
    }

    private fun parseRatingVotes(doc: Document): Int? = safe(null) {
        val scope = doc.selectFirst(SERIES_RATING)?.parent()?.text()
        if (scope == null) {
            null
        } else {
            Regex("""([0-9][0-9,]*)\s*votes""", RegexOption.IGNORE_CASE)
                .find(scope)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        }
    }

    private fun parseRecommendations(doc: Document): List<NuRecommendation> = safe(emptyList()) {
        // NU markup: <h5 class="seriesother">Recommendations</h5> followed by
        // sibling <a href="/series/..">Name</a> links separated by <br>, until
        // the next h5/heading.
        val header = doc.select("h5, h4, h3").firstOrNull {
            it.text().trim().equals("Recommendations", ignoreCase = true)
        }
        val recs = ArrayList<NuRecommendation>()
        if (header != null) {
            var node: Element? = header.nextElementSibling()
            while (node != null) {
                val tag = node.tagName().lowercase()
                if (tag == "h5" || tag == "h4" || tag == "h3" || tag == "h2") break
                if (tag == "a" && node.attr("href").contains("/series/")) {
                    val href = node.absUrl("href").ifBlank { node.attr("href") }
                    val name = node.text().trim()
                    if (href.isNotBlank() && name.isNotBlank()) {
                        // Recommendation links are text-only; NU's per-series
                        // cover follows a fixed CDN pattern keyed by the series
                        // id carried in the anchor's id="sidNNNNN".
                        val sid = SID.find(node.id())?.groupValues?.get(1)
                        recs += NuRecommendation(
                            title = name,
                            url = href,
                            coverUrl = node.selectFirst("img")?.imgSrc()
                                ?: sid?.let { CDN_COVER.format(it) },
                        )
                    }
                }
                node = node.nextElementSibling()
            }
        }
        recs.distinctBy { it.url }
    }

    private fun Element.imgSrc(): String? {
        val candidates = listOf("abs:src", "src", "abs:data-src", "data-src", "abs:data-cfsrc", "data-cfsrc")
        for (attr in candidates) {
            val v = if (attr.startsWith("abs:")) absUrl(attr.removePrefix("abs:")) else attr(attr)
            if (v.isNotBlank()) return v
        }
        return null
    }
}
