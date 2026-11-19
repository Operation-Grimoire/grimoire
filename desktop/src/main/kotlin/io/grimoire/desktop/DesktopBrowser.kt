package io.grimoire.desktop

import io.grimoire.sessions.BrowserCookieStore
import io.grimoire.sessions.BrowserSession
import io.grimoire.sessions.SessionCookie
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** What the HTTP platform needs from the browser, so it can be tested without Chromium. */
interface BrowserSessionHost {
    /** True once any sign-in or cookie write created the profile; HTTP then uses its cookies. */
    val profileInUse: Boolean

    /** Starts the engine if needed. Blocking; never call on the UI thread. */
    fun session(): StartedBrowser
}

class StartedBrowser(val store: BrowserCookieStore, val userAgent: String) {
    val session = BrowserSession(store)
}

/** Starts the browser helper on first use and owns it until app close. */
class DesktopBrowser(
    private val directory: Path,
    private val networkEnabled: AtomicBoolean,
    private val engineArgs: List<String> = emptyList(),
) : BrowserSessionHost {
    private val lock = Any()
    private var helper: BrowserHelperProcess? = null
    private var closed = false

    override val profileInUse: Boolean get() = Files.isDirectory(directory.resolve("profile"))
    val running: Boolean get() = synchronized(lock) { helper?.running == true }
    val helperPid: Long? get() = synchronized(lock) { helper?.pid }

    override fun session(): StartedBrowser = start().session()

    private fun start(): BrowserHelperProcess {
        check(!onUiThread()) { "The browser cannot start on the UI thread" }
        return synchronized(lock) {
            check(!closed) { "Grimoire is closing" }
            helper?.takeIf { it.alive }?.let { return@synchronized it }
            if (helper?.canReplace == false) throw IOException("The browser helper is stopping. Try again.")
            if (!Files.exists(directory.resolve("engine-$ENGINE_VERSION/install.lock")) && !networkEnabled.get()) {
                throw IOException("Go online once to install the browser engine")
            }
            BrowserHelperProcess.launch(directory, engineArgs).also { helper = it }
        }
    }

    /** Waits for the sign-in window to close and flush the profile. */
    fun signIn(title: String, url: String, successUrl: String?) {
        val process = start()
        process.session()
        process.request("signIn", title, url, successUrl, timeout = null)
    }

    fun close(deadline: Duration = Duration.ofSeconds(20)): BrowserShutdown {
        check(!onUiThread()) { "Close the browser off the UI thread" }
        val process = synchronized(lock) { closed = true; helper }
        return process?.close(deadline) ?: BrowserShutdown.GRACEFUL
    }

    internal fun resumeAfterFailedClose() {
        synchronized(lock) { closed = false }
    }

    companion object {
        const val ENGINE_VERSION = "jcef-152.0.6"
        internal val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val supported: Boolean = windows || System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

        private fun onUiThread() = Thread.currentThread().name.startsWith("AWT-EventQueue")
    }
}

internal class HelperCookieStore(private val helper: BrowserHelperProcess) : BrowserCookieStore {
    override fun cookiesForRequest(url: HttpUrl): List<Cookie> =
        readCookies("cookies", url).map { it.toCookie() }

    override fun setCookie(url: HttpUrl, header: String) {
        helper.request("setCookie", url.toString(), header)
    }

    override fun snapshot(url: HttpUrl): List<SessionCookie> = readCookies("snapshot", url)

    private fun readCookies(operation: String, url: HttpUrl): List<SessionCookie> =
        helper.request(operation, url.toString()).asJsonArray.map { browserJson.fromJson(it, SessionCookie::class.java) }

    override fun flush() {
        helper.request("flush")
    }
}
