package io.grimoire.desktop

import io.grimoire.api.network.SourceHttpPlatform
import okhttp3.CookieJar
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Public HTTP-only reading. Cookie login and rendered-page browsing are unavailable. */
class DesktopHttpPlatform(private val networkEnabled: AtomicBoolean) : SourceHttpPlatform {
    override val cookieJar: CookieJar = CookieJar.NO_COOKIES
    override val userAgent = "Grimoire-Desktop/0.1 (+https://github.com/Operation-Grimoire/grimoire)"
    override val interceptors = listOf(Interceptor { chain ->
        if (!networkEnabled.get()) throw IOException("Offline mode is enabled")
        chain.proceed(chain.request())
    })
}
