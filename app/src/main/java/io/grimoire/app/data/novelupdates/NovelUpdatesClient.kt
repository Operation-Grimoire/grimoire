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

    suspend fun search(query: String): List<NuSearchResult> {
        if (query.isBlank()) return emptyList()
        val body = get(NovelUpdatesEndpoints.searchUrl(query))
        return NovelUpdatesParser.parseSearch(Jsoup.parse(body, NovelUpdatesEndpoints.BASE_URL))
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
    }
}
