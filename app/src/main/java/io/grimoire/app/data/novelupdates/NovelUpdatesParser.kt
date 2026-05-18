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

    // --- Listing pagination (digg/wp-pagenavi style used by NU) ---
    private const val PAGINATION_NEXT = ".digg_pagination a.next_page, .pagination a.next_page"

    // --- /list-tags/ ---
    // Tolerant: any anchor into a tag page; the numeric series-finder tag id
    // is read from the anchor id / a data-attr / a trailing href number.
    // (Selectors pending a real /list-tags/ HTML sample to harden.)
    private const val TAG_LINK = "a[href*=/series-tags/], a[href*=/tags/], a[href*=/stags/]"
    private val TAG_ID = Regex("""(?:tag|tid|term)(\d+)""")
    private val TRAILING_NUM = Regex("""(\d+)/?$""")

    // --- Series ranking ("leaderboard") page ---
    // NU renders the ranking as a list/table distinct from the finder cards.
    // We scope to the narrowest plausible ranking container (so the sidebar's
    // "Latest Series" links don't leak in) and tolerate DOM drift.
    private const val RANKING_SCOPE =
        "table#myTable, table.tablesorter, div.ranking, div#rankingmain, div.l-content"
    private const val RANKING_LINK = "a[href*=/series/]"

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

    fun parseSearch(doc: Document): List<NuSearchResult> = parseCards(doc)

    /**
     * Series Finder browse listing — the same `search_main_box_nu` cards as
     * search, so it shares one code path.
     */
    fun parseListing(doc: Document): List<NuSearchResult> = parseCards(doc)

    /**
     * Ranking / Latest pages: their DOM is less certain than the finder, so
     * try the finder cards first and fall back to scoped series links so the
     * page degrades gracefully if NU's markup differs.
     */
    fun parseListingOrLinks(doc: Document): List<NuSearchResult> {
        val cards = parseCards(doc)
        return cards.ifEmpty { parseRanking(doc) }
    }

    private fun parseCards(doc: Document): List<NuSearchResult> = safe(emptyList()) {
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


    /**
     * Parses /list-tags/ into [NuTag]s (name + numeric id). Tolerant: tries
     * the anchor id, common data-attrs, then a trailing href number for the
     * series-finder tag id. Hardened once a real page sample is available.
     */
    fun parseTags(doc: Document): List<NuTag> = safe(emptyList()) {
        doc.select(TAG_LINK).mapNotNull { a ->
            val name = a.text().trim()
            if (name.isEmpty()) return@mapNotNull null
            val href = a.attr("href")
            val id = TAG_ID.find(a.id())?.groupValues?.get(1)
                ?: a.attr("data-id").ifBlank { null }
                ?: a.attr("data-tag-id").ifBlank { null }
                ?: a.attr("rel").ifBlank { null }?.takeIf { it.all(Char::isDigit) }
                ?: TRAILING_NUM.find(href)?.groupValues?.get(1)
            if (id.isNullOrBlank()) return@mapNotNull null
            NuTag(name = name, id = id)
        }.distinctBy { it.id }
    }

    /**
     * Series-ranking leaderboard rows. NU's ranking page has no finder cards,
     * so we take series links within the ranking container in document order
     * (= rank order), de-duplicated. Covers come from the row's <img> or the
     * fixed CDN pattern keyed by the anchor's `sidNNNNN` id.
     */
    fun parseRanking(doc: Document): List<NuSearchResult> = safe(emptyList()) {
        val scope = doc.selectFirst(RANKING_SCOPE) ?: doc.body() ?: doc
        scope.select(RANKING_LINK).mapNotNull { link ->
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            if (href.isBlank() || !href.contains("/series/")) return@mapNotNull null
            val title = link.text().trim()
            // Skip empty/decorative anchors (cover-only links, "more", etc.).
            if (title.length < 2) return@mapNotNull null
            val sid = SID.find(link.id())?.groupValues?.get(1)
            val row = link.closest("tr, li, div.search_main_box_nu, div") ?: link
            NuSearchResult(
                title = title,
                slug = NovelUpdatesEndpoints.slugFromUrl(href),
                url = href,
                coverUrl = row.selectFirst("img")?.imgSrc()
                    ?: sid?.let { CDN_COVER.format(it) },
            )
        }.distinctBy { it.url }
    }

    /** True if NU's pagination shows a "next page" link. */
    fun hasNextPage(doc: Document): Boolean = safe(false) {
        doc.selectFirst(PAGINATION_NEXT) != null
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
