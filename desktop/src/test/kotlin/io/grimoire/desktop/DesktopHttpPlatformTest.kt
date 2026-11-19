package io.grimoire.desktop

import io.grimoire.sessions.BrowserCookieStore
import io.grimoire.sessions.SessionCookie
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class DesktopHttpPlatformTest {
    private val server = MockWebServer().apply { start() }

    @After fun stop() = server.shutdown()

    /** Host-only cookies by name; enough to observe what the platform reads and writes. */
    private class MemoryStore : BrowserCookieStore {
        val cookies = linkedMapOf<String, Cookie>()
        var flushes = 0
        override fun cookiesForRequest(url: HttpUrl) = cookies.values.filter { it.matches(url) }
        override fun setCookie(url: HttpUrl, header: String) {
            val cookie = Cookie.parse(url, header) ?: return
            if (cookie.expiresAt <= System.currentTimeMillis()) cookies.remove(cookie.name) else cookies[cookie.name] = cookie
        }
        override fun snapshot(url: HttpUrl): List<SessionCookie> = error("unused")
        override fun flush() { flushes++ }
    }

    private class FakeBrowser(var inUse: Boolean) : BrowserSessionHost {
        val store = MemoryStore()
        var starts = 0
        override val profileInUse get() = inUse
        override fun session(): StartedBrowser { starts++; inUse = true; return started }
        private val started by lazy { StartedBrowser(store, "Chromium-Test/152") }
    }

    private fun client(platform: DesktopHttpPlatform) = OkHttpClient.Builder()
        .cookieJar(platform.cookieJar)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", platform.userAgent).build()) }
        .apply { platform.interceptors.forEach(::addInterceptor); platform.networkInterceptors.forEach(::addNetworkInterceptor) }
        .build()

    private fun get(client: OkHttpClient) = client.newCall(Request.Builder().url(server.url("/page")).build()).execute().use { it.code }

    @Test fun withoutAProfileRequestsCarryNoCookiesAndChromiumStaysOff() {
        val browser = FakeBrowser(inUse = false)
        val platform = DesktopHttpPlatform(AtomicBoolean(true), browser)
        server.enqueue(MockResponse().addHeader("Set-Cookie", "anonymous=1; Path=/"))
        server.enqueue(MockResponse())
        val client = client(platform)
        get(client); get(client)
        server.takeRequest()
        val second = server.takeRequest()
        assertNull(second.getHeader("Cookie"))
        assertEquals(DesktopHttpPlatform.DEFAULT_USER_AGENT, second.getHeader("User-Agent"))
        assertEquals(0, browser.starts)
        assertTrue(browser.store.cookies.isEmpty())
    }

    @Test fun signedInProfileSharesCookiesAndUserAgentWithHttp() {
        val browser = FakeBrowser(inUse = true)
        browser.store.setCookie(server.url("/"), "login=browser; Path=/")
        val platform = DesktopHttpPlatform(AtomicBoolean(true), browser)
        server.enqueue(MockResponse().addHeader("Set-Cookie", "login=rotated; Path=/"))
        server.enqueue(MockResponse())
        val client = client(platform)
        get(client); get(client)
        val first = server.takeRequest()
        assertEquals("login=browser", first.getHeader("Cookie"))
        assertEquals("Chromium-Test/152", first.getHeader("User-Agent"))
        // The response's Set-Cookie reached the browser store, and the next request used it.
        assertEquals("login=rotated", server.takeRequest().getHeader("Cookie"))
    }

    @Test fun sourceCookieWritesStartTheBrowserAndLogoutExpiresThem() {
        val browser = FakeBrowser(inUse = false)
        val cookies = DesktopHttpPlatform(AtomicBoolean(true), browser).cookies
        val url = server.url("/")
        cookies.setCookie(url, "uid=397; Path=/")
        assertTrue(browser.inUse)
        assertEquals("uid=397", cookies.cookieHeader(url))
        cookies.setCookie(url, "uid=; Max-Age=0; Path=/")
        cookies.flush()
        assertEquals("", cookies.cookieHeader(url))
        assertEquals(1, browser.store.flushes)
    }

    @Test fun withoutABrowserSourceCookiesKeepTheOldBehaviour() {
        val cookies = DesktopHttpPlatform(AtomicBoolean(true)).cookies
        cookies.setCookie(server.url("/"), "uid=397; Path=/")
        assertEquals("", cookies.cookieHeader(server.url("/")))
    }

    @Test fun offlineRejectsRequestsBeforeTheBrowserIsTouched() {
        val browser = FakeBrowser(inUse = true)
        val client = client(DesktopHttpPlatform(AtomicBoolean(false), browser))
        val failure = runCatching { get(client) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(0, server.requestCount)
    }
}
