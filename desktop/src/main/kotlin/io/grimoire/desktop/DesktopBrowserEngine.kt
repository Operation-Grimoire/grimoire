package io.grimoire.desktop

import com.google.gson.JsonParser
import io.grimoire.sessions.jcef.JcefCookieStore
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.SessionDevToolsBridge
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

internal class DesktopBrowserEngine private constructor(
    private val app: CefApp,
    private val client: org.cef.CefClient,
    private val sessionBrowser: CefBrowser,
    private val sessionHolder: javax.swing.JWindow,
    private val bridge: SessionDevToolsBridge,
    val store: JcefCookieStore,
    private val terminated: CompletableFuture<Unit>,
    private val state: EngineState,
    val userAgent: String,
) {
    fun openWindow(title: String, url: String, successUrl: String?, finished: CompletableFuture<Unit>) {
        check(!state.closing.get()) { "Grimoire is closing" }
        SwingUtilities.invokeAndWait {
            val browser = client.createBrowser(url, false, false)
            val window = JFrame(title)
            val signIn = SignInWindow(browser, window, url, successUrl, finished)
            state.windows[browser] = signIn
            val done = JButton("Done")
            done.addActionListener { signIn.finish() }
            window.layout = BorderLayout()
            window.add(JPanel(BorderLayout()).apply {
                add(JLabel("  Sign in, then press Done if this window does not close by itself."), BorderLayout.CENTER)
                add(done, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            window.add(browser.uiComponent, BorderLayout.CENTER)
            window.setSize(1000, 760)
            window.setLocationRelativeTo(null)
            window.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) = signIn.finish()
            })
            window.isVisible = true
        }
    }

    fun shutdown(deadline: Duration): Boolean {
        state.closing.set(true)
        try { store.flush() } catch (_: Exception) {}
        runCatching { store.close() }
        runCatching { bridge.close() }
        val until = System.nanoTime() + deadline.toNanos()
        SwingUtilities.invokeAndWait {
            state.windows.values.toList().forEach { it.finish() }
            sessionBrowser.setCloseAllowed()
            sessionBrowser.close(true)
        }
        // The last browser's onBeforeClose normally disposes the app. On Linux the hidden
        // cookie browser may never report closing, so dispose anyway after a grace period.
        if (!state.allClosed.await(BROWSER_CLOSE_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
            SwingUtilities.invokeLater { state.dispose(app) }
        }
        SwingUtilities.invokeLater { sessionHolder.dispose() }
        return try {
            terminated.get((until - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS); true
        } catch (_: java.util.concurrent.TimeoutException) {
            System.getLogger("DesktopBrowser").log(System.Logger.Level.WARNING,
                "Browser engine did not shut down within ${deadline.seconds} s (state ${CefApp.getState()})")
            false
        }
    }

    companion object {
        private val BROWSER_CLOSE_GRACE: Duration = Duration.ofSeconds(5)
        private val QUIET_ENGINE_ARGS = listOf(
            "--no-first-run", "--no-default-browser-check", "--password-store=basic", "--use-mock-keychain",
        )

        fun start(profile: Path, natives: Path, args: List<String>): DesktopBrowserEngine {
            val terminated = CompletableFuture<Unit>()
            val state = EngineState()
            val builder = CefAppBuilder()
            builder.setInstallDir(natives.toFile())
            builder.cefSettings.windowless_rendering_enabled = false
            builder.cefSettings.root_cache_path = profile.toString()
            builder.cefSettings.cache_path = profile.resolve("default").toString()
            builder.cefSettings.log_file = profile.resolve("cef.log").toString()
            // An embedded browser must never stop on Chromium's first-run, default-browser or
            // keyring prompts; on Linux they block startup. Cookies stay in the app's own profile.
            builder.addJcefArgs(*(QUIET_ENGINE_ARGS + args).toTypedArray())
            builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
                override fun stateHasChanged(next: CefApp.CefAppState) {
                    if (next == CefApp.CefAppState.TERMINATED) terminated.complete(Unit)
                }
            })
            val app = builder.build()
            val client = app.createClient()
            val created = CompletableFuture<CefBrowser>()
            val open = ConcurrentHashMap.newKeySet<CefBrowser>()
            client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                override fun onAfterCreated(browser: CefBrowser) {
                    open += browser
                    if (state.windows[browser] == null) created.complete(browser)
                }

                // Pop-ups (some OAuth flows) open in the same sign-in window.
                override fun onBeforePopup(browser: CefBrowser, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                    if (!targetUrl.isNullOrBlank()) browser.loadURL(targetUrl)
                    return true
                }

                override fun onBeforeClose(browser: CefBrowser) {
                    open -= browser
                    state.windows.remove(browser)?.closed()
                    // JCEF finishes client cleanup after this callback; disposing here avoids racing it.
                    if (state.closing.get() && open.isEmpty()) { state.allClosed.countDown(); state.dispose(app) }
                }
            })
            client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                    if (frame.isMain) state.windows[browser]?.addressChanged(url)
                }
            })
            val session = client.createBrowser("about:blank", false, false)
            // The cookie browser needs a native parent window, or Linux never reports it closed
            // and shutdown stalls. The 1x1 window is realized but never shown.
            lateinit var holder: javax.swing.JWindow
            SwingUtilities.invokeAndWait {
                holder = javax.swing.JWindow()
                holder.contentPane.add(session.uiComponent)
                holder.setBounds(-100, -100, 1, 1)
                holder.addNotify()
            }
            session.createImmediately()
            val ready = try { created.get(30, TimeUnit.SECONDS) } catch (failure: Exception) {
                app.dispose(); throw IOException("The browser engine did not start", failure)
            }
            val store = JcefCookieStore(ready)
            val bridge = SessionDevToolsBridge(ready)
            // Cloudflare binds clearance cookies to the browser's User-Agent; HTTP uses the same one.
            val userAgent = JsonParser.parseString(bridge.execute("Browser.getVersion", null).get(10, TimeUnit.SECONDS))
                .asJsonObject.get("userAgent").asString
            return DesktopBrowserEngine(app, client, ready, holder, bridge, store, terminated, state, userAgent)
        }
    }
}

private class EngineState {
    val closing = AtomicBoolean()
    val windows = ConcurrentHashMap<CefBrowser, SignInWindow>()
    val allClosed = java.util.concurrent.CountDownLatch(1)
    private val disposed = AtomicBoolean()
    fun dispose(app: CefApp) { if (disposed.compareAndSet(false, true)) app.dispose() }
}

private class SignInWindow(
    private val browser: CefBrowser,
    private val window: JFrame,
    private val startUrl: String,
    private val successUrl: String?,
    private val finished: CompletableFuture<Unit>,
) {
    private val finishing = AtomicBoolean()

    fun addressChanged(url: String) {
        if (successUrl != null && url.startsWith(successUrl) && !url.startsWith(startUrl)) {
            SwingUtilities.invokeLater { finish() }
        }
    }

    /** UI thread only. */
    fun finish() {
        if (!finishing.compareAndSet(false, true)) return
        // Hiding a JCEF window also sends a native visibility update on macOS.
        // Keep its host visible until Chromium reports that the browser has closed.
        browser.setCloseAllowed()
        browser.close(true)
    }

    fun closed() {
        SwingUtilities.invokeLater { window.dispose() }
        finished.complete(Unit)
    }
}
