package io.grimoire.app.data.novelupdates

import io.grimoire.api.network.defaultOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP access to NovelUpdates. Reuses the extension network stack
 * ([defaultOkHttpClient]) so requests share the system WebView cookie jar
 * (a future logged-in session "just works"), the correct User-Agent and the
 * Cloudflare interceptor.
 *
 * Requests are serialized through a single mutex with a minimum spacing so we
 * never hammer NovelUpdates (it has no API and will rate-limit / ban).
 */
@Singleton
class NovelUpdatesClient @Inject constructor() {

    private val client: OkHttpClient = defaultOkHttpClient()
    private val gate = Mutex()
    @Volatile private var lastRequestAt = 0L

    /**
     * NovelUpdates' Series Finder matches `sh` as a case-insensitive
     * *contiguous substring* of the title, so a source title that differs from
     * NU's canonical title even slightly (extra "(LN)", different punctuation,
     * appended words) returns nothing. We therefore try progressively simpler
     * query variants and return the first that yields results.
     */
    suspend fun search(query: String): List<NuSearchResult> {
        for (variant in searchVariants(query)) {
            val body = get(NovelUpdatesEndpoints.searchUrl(variant))
            val results = NovelUpdatesParser.parseSearch(
                Jsoup.parse(body, NovelUpdatesEndpoints.BASE_URL),
            )
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    /** Series Finder search/filter listing (one page). */
    suspend fun finder(filter: NuBrowseFilter, page: Int): NuListingPage {
        val doc = Jsoup.parse(
            get(NovelUpdatesEndpoints.seriesFinderUrl(filter, page)),
            NovelUpdatesEndpoints.BASE_URL,
        )
        return NuListingPage(
            results = NovelUpdatesParser.parseListing(doc),
            hasNext = NovelUpdatesParser.hasNextPage(doc),
        )
    }

    /**
     * Series Ranking page. Falls back to Series Finder sorted by rank if the
     * ranking page can't be parsed, so the page is never empty.
     */
    suspend fun ranking(type: NuRankingType, filter: NuListingFilter, page: Int): NuListingPage {
        val doc = Jsoup.parse(
            get(NovelUpdatesEndpoints.seriesRankingUrl(type, filter, page)),
            NovelUpdatesEndpoints.BASE_URL,
        )
        val results = NovelUpdatesParser.parseListingOrLinks(doc)
        if (results.isNotEmpty()) {
            return NuListingPage(results, NovelUpdatesParser.hasNextPage(doc))
        }
        return finder(
            NuBrowseFilter(
                sort = NuBrowseSort.RANK,
                languages = filter.languages,
                genresInclude = filter.genres,
                genresMatchAll = filter.genresMatchAll,
            ),
            page,
        )
    }

    /** Latest Series page. */
    suspend fun latest(filter: NuListingFilter, page: Int): NuListingPage {
        val doc = Jsoup.parse(
            get(NovelUpdatesEndpoints.latestSeriesUrl(filter, page)),
            NovelUpdatesEndpoints.BASE_URL,
        )
        val results = NovelUpdatesParser.parseListingOrLinks(doc)
        if (results.isNotEmpty()) {
            return NuListingPage(results, NovelUpdatesParser.hasNextPage(doc))
        }
        return finder(
            NuBrowseFilter(
                sort = NuBrowseSort.LAST_UPDATED,
                languages = filter.languages,
                genresInclude = filter.genres,
                genresMatchAll = filter.genresMatchAll,
            ),
            page,
        )
    }

    /**
     * All NovelUpdates tags (loaded live from the paginated /list-tags/).
     * Walks pages while a next link exists; capped for safety.
     */
    suspend fun listTags(): List<NuTag> {
        val out = ArrayList<NuTag>()
        var page = 1
        while (page <= MAX_TAG_PAGES) {
            val doc = Jsoup.parse(
                get(NovelUpdatesEndpoints.listTagsUrl(page)),
                NovelUpdatesEndpoints.BASE_URL,
            )
            out += NovelUpdatesParser.parseTags(doc)
            if (!NovelUpdatesParser.hasNextPage(doc)) break
            page++
        }
        return out.distinctBy { it.slug }
    }

    suspend fun getSeries(slugOrUrl: String): NuSeries {
        val url = NovelUpdatesEndpoints.seriesUrl(slugOrUrl)
        val body = get(url)
        return NovelUpdatesParser.parseSeries(Jsoup.parse(body, url), url)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        gate.withLock {
            val since = System.currentTimeMillis() - lastRequestAt
            if (since in 0 until MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - since)
            lastRequestAt = System.currentTimeMillis()
        }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("NovelUpdates HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_200L
        const val MAX_TAG_PAGES = 30
    }
}

private const val MAX_VARIANTS = 7
private val PAREN = Regex("""\(.*?\)""")
private val WS = Regex("""\s+""")
private val LEADING_ARTICLE = Regex("""^(the|a|an)\s+""", RegexOption.IGNORE_CASE)

/**
 * Ordered, de-duplicated NovelUpdates search queries derived from a title,
 * from most to least specific. The Series Finder matches `sh` as a contiguous
 * case-insensitive substring of the title, so trying simpler variants greatly
 * improves the hit rate when the source title isn't an exact NU title.
 */
internal fun searchVariants(raw: String): List<String> {
    val t = raw.trim()
    if (t.isEmpty()) return emptyList()

    val noParen = t.replace(PAREN, " ").replace(WS, " ").trim()
    val beforeColon = noParen.substringBefore(':').trim()

    val out = LinkedHashSet<String>()
    fun add(s: String) {
        val v = s.trim()
        if (v.length >= 3) out.add(v)
    }

    add(t)
    add(noParen)
    add(beforeColon)
    add(beforeColon.replace(LEADING_ARTICLE, ""))

    // Leading word n-grams of the cleaned title are very likely to be a
    // contiguous substring of NU's canonical title.
    val words = noParen.replace(LEADING_ARTICLE, "").split(WS).filter { it.isNotBlank() }
    for (k in minOf(8, words.size) downTo 3) {
        add(words.take(k).joinToString(" "))
    }

    return out.toList().take(MAX_VARIANTS)
}
