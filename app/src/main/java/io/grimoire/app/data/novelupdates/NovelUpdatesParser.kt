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
    private const val SEARCH_RESULT = "div.search_main_box_nu"
    private const val SEARCH_TITLE_LINK = "div.search_body_nu div.search_title a"
    private const val SEARCH_IMG = "div.search_img_nu img"

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
        val header = doc.select("h5, h4, h2").firstOrNull {
            it.text().trim().equals("Recommendations", ignoreCase = true)
        }
        var result = emptyList<NuRecommendation>()
        if (header != null) {
            // Walk forward from the header to the first block with series links.
            var node: Element? = header.nextElementSibling()
            var hops = 0
            while (node != null && hops < 6) {
                val anchors = node.select("a[href*=/series/]")
                if (anchors.isNotEmpty()) {
                    result = anchors.mapNotNull { a ->
                        val href = a.absUrl("href").ifBlank { a.attr("href") }
                        if (href.isBlank()) {
                            null
                        } else {
                            val img = a.selectFirst("img")
                            val name = a.attr("title")
                                .ifBlank { img?.attr("alt") ?: a.text() }
                                .trim()
                            if (name.isBlank()) {
                                null
                            } else {
                                NuRecommendation(
                                    title = name,
                                    url = href,
                                    coverUrl = img?.imgSrc(),
                                )
                            }
                        }
                    }.distinctBy { it.url }
                    break
                }
                node = node.nextElementSibling()
                hops++
            }
        }
        result
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
