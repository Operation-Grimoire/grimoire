package io.grimoire.desktop

import io.grimoire.api.network.SourceBrowserCookies
import io.grimoire.api.network.SourceCookieStore
import io.grimoire.api.network.SourceHttpNetworkInterceptors
import io.grimoire.api.network.SourceHttpPlatform
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Source HTTP for Desktop. Without a [browser], or before its profile exists, requests carry no
 * cookies. Once a sign-in or a source's cookie write creates the profile, every source request
 * shares the embedded browser's cookies and User-Agent, as on Android.
 */
class DesktopHttpPlatform(
    private val networkEnabled: AtomicBoolean,
    private val browser: BrowserSessionHost? = null,
) : SourceHttpPlatform, SourceHttpNetworkInterceptors, SourceBrowserCookies {
    private fun started(): StartedBrowser? = browser?.takeIf { it.profileInUse }?.session()

    // Offline requests are rejected anyway; do not start Chromium for them.
    override val userAgent: String get() = (if (networkEnabled.get()) started()?.userAgent else null) ?: DEFAULT_USER_AGENT

    override val interceptors = listOf(Interceptor { chain ->
        if (!networkEnabled.get()) throw IOException("Offline mode is enabled")
        chain.proceed(chain.request())
    })

    override val cookieJar: CookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> = started()?.session?.cookieJar?.loadForRequest(url).orEmpty()
        // Responses are written by the network interceptor, which keeps the raw Set-Cookie attributes.
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
    }

    override val networkInterceptors = listOf(Interceptor { chain ->
        val running = started() ?: return@Interceptor chain.proceed(chain.request())
        running.session.networkInterceptor.intercept(chain)
    })

    /** Source cookie reads and writes start the browser, so sign-in state survives restarts. */
    /** Without a browser this keeps the old behaviour: no cookies, writes ignored. */
    override val cookies: SourceCookieStore = object : SourceCookieStore {
        private fun store() = browser?.session()?.store
        override fun cookieHeader(url: HttpUrl): String =
            store()?.cookiesForRequest(url).orEmpty().joinToString("; ") { "${it.name}=${it.value}" }
        override fun setCookie(url: HttpUrl, header: String) { store()?.setCookie(url, header) }
        override fun flush() { store()?.flush() }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Grimoire-Desktop/0.1 (+https://github.com/Operation-Grimoire/grimoire)"
    }
}
