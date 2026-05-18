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

    // --- Series Finder tag list --- per seriesfinder.js the tag filter is
    //   $("#tags_include").chosen() over a <select> of <option value=id>name.
    private const val TAG_OPTION = "select#tags_include option, select#tags_exclude option"

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
    private const val SERIES_TYPE = "#showtype"
    // NU encodes durable metadata as tokens on the post container's class
    // attribute (e.g. "language-japanese ntype-web-novel post-8456").
    private const val SERIES_POST_ROOT = "div.w-blog[class*=post-]"
    private val LANGUAGE_CLASS = Regex("""(?:^|\s)language-([a-z0-9-]+)""")
    private const val SERIES_AUTHORS = "#showauthors a"
    private const val SERIES_ARTISTS = "#showartists a"
    private const val SERIES_ASSOCIATED = "#editassociated"
    private const val SERIES_DESCRIPTION = "#editdescription"
    private const val SERIES_GENRES = "#seriesgenre a"
    private const val SERIES_TAGS = "#showtags a"
    private const val SERIES_STATUS = "#editstatus"
    private const val SERIES_YEAR = "#edityear"
    private const val SERIES_LICENSED = "#showlicensed"
    private const val SERIES_TRANSLATED = "#showtranslated"
    private const val SERIES_OPUBLISHER = "#showopublisher a"
    private const val SERIES_EPUBLISHER = "#showepublisher a"
    private const val SERIES_RLIST = "b.rlist"
    private const val SERIES_RELEASE_ROW = "table#myTable tbody tr"
    private const val SERIES_REVIEW_COUNT = "div.review-count"
    private const val SERIES_RATING = "span.uvotes"

    // --- Series reviews (the #comments / .w-comments list) ---
    private const val REVIEW_ITEM = "div.w-comments-item"
    private const val REVIEW_PAGE_LINK = ".w-comments-pagination .page-numbers"
    private val PERM_SID = Regex("""sid=(\d+)""")

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
        doc.select(TAG_OPTION).mapNotNull { opt ->
            val id = opt.attr("value").trim()
            val name = opt.text().trim()
            if (id.isEmpty() || !id.all(Char::isDigit) || name.isEmpty()) {
                return@mapNotNull null
            }
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
        val title = doc.selectFirst(SERIES_TITLE)?.text()?.trim()
            ?.ifBlank { null }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        return NuSeries(
            slug = slug,
            url = requestUrl,
            title = title,
            type = doc.selectFirst(SERIES_TYPE)?.text()?.trim()?.ifBlank { null },
            language = parseLanguage(doc),
            authors = textList(doc, SERIES_AUTHORS),
            artists = textList(doc, SERIES_ARTISTS),
            associatedNames = parseAssociated(doc),
            description = (doc.selectFirst(SERIES_DESCRIPTION)?.text()?.trim()?.ifBlank { null })
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content")
                    ?.trim()?.ifBlank { null },
            genres = doc.select(SERIES_GENRES).map { it.text().trim() }.filter { it.isNotBlank() }.distinct(),
            tags = doc.select(SERIES_TAGS).map { it.text().trim() }.filter { it.isNotBlank() }.distinct(),
            status = parseLines(doc.selectFirst(SERIES_STATUS))?.joinToString("\n"),
            year = doc.selectFirst(SERIES_YEAR)?.text()?.trim()?.ifBlank { null },
            originalPublishers = textList(doc, SERIES_OPUBLISHER),
            englishPublishers = textList(doc, SERIES_EPUBLISHER)
                .filterNot { it.equals("N/A", ignoreCase = true) },
            releaseFrequency = textAfterHeading(doc, "Release Frequency"),
            licensed = parseYesNo(doc.selectFirst(SERIES_LICENSED)?.text()),
            completelyTranslated = parseYesNo(doc.selectFirst(SERIES_TRANSLATED)?.text()),
            readingListCount = doc.selectFirst(SERIES_RLIST)?.text()
                ?.replace(",", "")?.trim()?.toIntOrNull(),
            rating = parseRating(doc),
            ratingVotes = parseRatingVotes(doc),
            coverUrl = doc.selectFirst(SERIES_COVER)?.imgSrc()
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
                    ?.trim()?.ifBlank { null },
            recommendations = parseRecommendations(doc),
            releases = parseReleases(doc),
            sid = parseSid(doc),
            reviews = parseReviews(doc),
            reviewCount = doc.selectFirst(SERIES_REVIEW_COUNT)?.text()
                ?.let { Regex("""[0-9][0-9,]*""").find(it)?.value?.replace(",", "")?.toIntOrNull() },
            reviewPageCount = parseReviewPageCount(doc),
        )
    }

    private fun textList(doc: Document, selector: String): List<String> = safe(emptyList()) {
        doc.select(selector)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun parseYesNo(text: String?): Boolean? = when (text?.trim()?.lowercase()) {
        "yes" -> true
        "no" -> false
        else -> null
    }

    /** Splits a `<br>`-separated block (e.g. #editstatus) into trimmed lines. */
    private fun parseLines(el: Element?): List<String>? = safe(null) {
        if (el == null) return@safe null
        el.html()
            .split("<br>", "<br/>", "<br />", ignoreCase = true)
            .map { org.jsoup.Jsoup.parse(it).text().trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { null }
    }

    /**
     * NU renders a few values as bare text right after their `<h5>` heading
     * (e.g. "Release Frequency"). Collect the text nodes between that heading
     * and the next element.
     */
    private fun textAfterHeading(doc: Document, label: String): String? = safe(null) {
        val heading = doc.select("h5.seriesother").firstOrNull {
            it.text().trim().equals(label, ignoreCase = true)
        } ?: return@safe null
        val sb = StringBuilder()
        var node = heading.nextSibling()
        while (node != null) {
            if (node is org.jsoup.nodes.Element) break
            if (node is org.jsoup.nodes.TextNode) sb.append(node.text())
            node = node.nextSibling()
        }
        sb.toString().trim().ifBlank { null }
    }

    private fun parseReleases(doc: Document): List<NuRelease> = safe(emptyList()) {
        doc.select(SERIES_RELEASE_ROW).mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 3) return@mapNotNull null
            val date = cells[0].text().trim()
            val groupEl = cells[1].selectFirst("a")
            val group = (groupEl?.text() ?: cells[1].text()).trim()
            val chapterEl = cells[2].selectFirst("span")
            val chapter = (chapterEl?.attr("title")?.ifBlank { null }
                ?: chapterEl?.text()
                ?: cells[2].text()).trim()
            if (date.isBlank() && chapter.isBlank()) return@mapNotNull null
            NuRelease(
                date = date,
                group = group,
                groupUrl = groupEl?.absUrl("href")?.ifBlank { groupEl.attr("href") }
                    ?.let(::absolutize),
                chapter = chapter,
            )
        }
    }

    /** Original language, read from the durable `language-…` post class. */
    private fun parseLanguage(doc: Document): String? = safe(null) {
        val classes = doc.selectFirst(SERIES_POST_ROOT)?.className() ?: return@safe null
        LANGUAGE_CLASS.find(classes)?.groupValues?.get(1)
            ?.split("-")
            ?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            ?.ifBlank { null }
    }

    private fun parseSid(doc: Document): String? = safe(null) {
        doc.select("a.permrev[href*=sid=]").firstNotNullOfOrNull { a ->
            PERM_SID.find(a.attr("href"))?.groupValues?.get(1)
        }
    }

    /** Highest numbered review page (1 when there's only a single page). */
    private fun parseReviewPageCount(doc: Document): Int = safe(1) {
        doc.select(REVIEW_PAGE_LINK)
            .mapNotNull { it.text().trim().replace(",", "").toIntOrNull() }
            .maxOrNull() ?: 1
    }

    private fun parseReviews(doc: Document): List<NuReview> = safe(emptyList()) {
        doc.select(REVIEW_ITEM).mapNotNull { item ->
            val id = item.id().removePrefix("comment-").trim()
                .ifBlank { return@mapNotNull null }

            val userLink = item.selectFirst("a[href*=/user/]")
            val avatar = item.selectFirst("div.rev_left img")
            val author = (avatar?.attr("alt")?.trim().orEmpty())
                .ifBlank { userLink?.text()?.trim().orEmpty() }
                .ifBlank { return@mapNotNull null }

            val filled = item.select("div.w-comments-item-meta-new i.fa-star").size
            val empties = item.select("div.w-comments-item-meta-new i.fa-star-o").size
            val rating = if (filled + empties > 0) filled.coerceIn(0, 5) else null

            val meta = item.selectFirst("div.w-comments-item-meta-new")
            val date = meta?.selectFirst("td[style*=right] div")
                ?.text()?.trim()?.ifBlank { null }
            val progress = item.selectFirst("span[id^=stat]")
                ?.text()?.trim()
                ?.takeUnless { it.isBlank() || it == "--" || it == "-" }

            val body = item.selectFirst("div.w-comments-item-text")
                ?.let(::cleanReviewText).orEmpty()
            if (body.isBlank()) return@mapNotNull null

            // Likes + permalink live in the sibling .rev_b1 bar; fall back to
            // a document-wide lookup keyed by the comment id if the DOM differs.
            val bar = item.nextElementSibling()?.takeIf { it.hasClass("rev_b1") }
            val likes = (bar?.selectFirst("span[class^=liked_]")
                ?: doc.selectFirst("span.liked_$id"))
                ?.text()?.trim()?.replace(",", "")?.toIntOrNull()
            val permalink = (bar?.selectFirst("a.permrev")
                ?: doc.selectFirst("a.permrev[href*=comid=$id]"))
                ?.attr("href")?.let(::absolutize)

            NuReview(
                id = id,
                author = author,
                authorUrl = userLink?.attr("href")?.let(::absolutize),
                avatarUrl = avatar?.imgSrc(),
                rating = rating,
                date = date,
                progress = progress,
                body = body,
                likes = likes,
                permalink = permalink,
            )
        }
    }

    /**
     * Flattens a review body to readable plain text: drops the "more>>" /
     * spoiler toggle scaffolding (keeping the hidden continuation and spoiler
     * prose) and turns block tags into line breaks.
     */
    private fun cleanReviewText(el: Element): String {
        val clone = el.clone()
        clone.select(".morelink, .dots, .spdiv, .sp-head").remove()
        val withBreaks = clone.html()
            .replace(Regex("(?is)<\\s*(br|/p|/div|/li)[^>]*>"), "\n")
        return org.jsoup.Jsoup.parse(withBreaks).wholeText()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun absolutize(href: String): String? = href.trim().ifBlank { null }?.let {
        when {
            it.startsWith("http", ignoreCase = true) -> it
            it.startsWith("//") -> "https:$it"
            it.startsWith("/") -> "https://www.novelupdates.com$it"
            else -> it
        }
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
