package io.grimoire.desktop

import com.google.gson.JsonParser
import io.grimoire.sessions.BrowserCookieStore
import io.grimoire.sessions.BrowserSession
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
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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

/**
 * Embedded Chromium (JCEF) for source sign-in and browser cookies. One engine per app run:
 * it starts on first use and shuts down once, when the app closes.
 */
class DesktopBrowser(
    directory: Path,
    private val networkEnabled: AtomicBoolean,
    private val engineArgs: List<String> = emptyList(),
) : BrowserSessionHost {
    private val profile = directory.resolve("profile")
    private val natives = directory.resolve("engine-$ENGINE_VERSION")
    private val lockFile = directory.resolve("profile.lock")
    private val lock = Any()
    private var engine: Engine? = null
    // Native startup runs outside [lock], so close() never waits behind a stuck start.
    private var starting: CompletableFuture<Engine>? = null
    private var profileLock: Pair<FileChannel, FileLock>? = null
    private var closed = false

    override val profileInUse: Boolean get() = Files.isDirectory(profile)

    val running: Boolean get() = synchronized(lock) { engine != null }

    override fun session(): StartedBrowser = start().started

    private fun start(): Engine {
        check(!SwingUtilities.isEventDispatchThread()) { "The browser cannot start on the UI thread" }
        val (pending, owner) = synchronized(lock) {
            check(!closed) { "Grimoire is closing" }
            engine?.let { return it }
            starting?.let { return@synchronized it to false }
            if (!Files.exists(natives.resolve("install.lock")) && !networkEnabled.get()) {
                throw IOException("Go online once to install the browser engine")
            }
            Files.createDirectories(profile)
            val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val held = try { channel.tryLock() }
                catch (_: java.nio.channels.OverlappingFileLockException) { null }
                catch (failure: Exception) { channel.close(); throw failure }
            if (held == null) {
                channel.close()
                throw IOException("Another Grimoire window is using the browser profile")
            }
            profileLock = channel to held
            CompletableFuture<Engine>().also { starting = it } to true
        }
        if (owner) {
            try {
                val started = Engine.start(profile, natives, engineArgs)
                val keep = synchronized(lock) { starting = null; if (!closed) { engine = started; true } else false }
                if (!keep) {
                    // close() ran during startup and is waiting for this engine to end.
                    pending.complete(started)
                    throw IOException("Grimoire is closing")
                }
                pending.complete(started)
            } catch (failure: Throwable) {
                synchronized(lock) { if (starting === pending) starting = null; if (!pending.isDone) releaseProfile() }
                pending.completeExceptionally(failure)
                throw failure
            }
        }
        return try { pending.get() } catch (failure: java.util.concurrent.ExecutionException) { throw failure.cause ?: failure }
    }

    /**
     * Opens [url] in a sign-in window and returns when it closes: automatically on the first
     * address that starts with [successUrl] (other than the start page), or when the user
     * presses Done or closes the window. Cookies are flushed before returning.
     */
    fun signIn(title: String, url: String, successUrl: String?) {
        val running = start()
        val finished = CompletableFuture<Unit>()
        running.openWindow(title, url, successUrl, finished)
        try { finished.get() } finally { runCatching { running.started.store.flush() } }
    }

    /** Returns false if native shutdown missed [deadline]; the caller then ends the process. */
    fun close(deadline: Duration = Duration.ofSeconds(20)): Boolean {
        check(!SwingUtilities.isEventDispatchThread()) { "Close the browser off the UI thread" }
        val until = System.nanoTime() + deadline.toNanos()
        val (running, pending) = synchronized(lock) { closed = true; engine.also { engine = null } to starting }
        try {
            val engine = running ?: pending?.let { future ->
                // Wait for a start in progress, within the same deadline.
                try { future.get(deadline.toMillis(), TimeUnit.MILLISECONDS) }
                catch (_: java.util.concurrent.TimeoutException) {
                    System.getLogger("DesktopBrowser").log(System.Logger.Level.WARNING,
                        "Browser engine was still starting at close (state ${CefApp.getState()})")
                    return false
                } catch (_: Exception) { null }
            } ?: return true
            return engine.shutdown(Duration.ofNanos((until - System.nanoTime()).coerceAtLeast(0)))
        } finally {
            synchronized(lock) { releaseProfile() }
        }
    }

    private fun releaseProfile() {
        profileLock?.let { (channel, held) -> runCatching { held.release() }; runCatching { channel.close() } }
        profileLock = null
    }

    private class Engine(
        private val app: CefApp,
        private val client: org.cef.CefClient,
        private val sessionBrowser: CefBrowser,
        private val bridge: SessionDevToolsBridge,
        private val store: JcefCookieStore,
        private val terminated: CompletableFuture<Unit>,
        private val state: EngineState,
        val started: StartedBrowser,
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
            SwingUtilities.invokeAndWait {
                state.windows.values.toList().forEach { it.finish() }
                sessionBrowser.close(true)
            }
            // The last browser's onBeforeClose disposes the app; wait for native termination.
            return try {
                terminated.get(deadline.toMillis(), TimeUnit.MILLISECONDS); true
            } catch (_: java.util.concurrent.TimeoutException) {
                System.getLogger("DesktopBrowser").log(System.Logger.Level.WARNING,
                    "Browser engine did not shut down within ${deadline.seconds} s (state ${CefApp.getState()})")
                false
            }
        }

        companion object {
            fun start(profile: Path, natives: Path, args: List<String>): Engine {
                val terminated = CompletableFuture<Unit>()
                val state = EngineState()
                val builder = CefAppBuilder()
                builder.setInstallDir(natives.toFile())
                builder.cefSettings.windowless_rendering_enabled = false
                builder.cefSettings.root_cache_path = profile.toString()
                builder.cefSettings.cache_path = profile.resolve("default").toString()
                builder.cefSettings.log_file = profile.resolve("cef.log").toString()
                if (args.isNotEmpty()) builder.addJcefArgs(*args.toTypedArray())
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
                        if (state.closing.get() && open.isEmpty()) app.dispose()
                    }
                })
                client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                    override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                        if (frame.isMain) state.windows[browser]?.addressChanged(url)
                    }
                })
                val session = client.createBrowser("about:blank", false, false)
                session.createImmediately()
                val ready = try { created.get(30, TimeUnit.SECONDS) } catch (failure: Exception) {
                    app.dispose(); throw IOException("The browser engine did not start", failure)
                }
                val store = JcefCookieStore(ready)
                val bridge = SessionDevToolsBridge(ready)
                // Cloudflare binds clearance cookies to the browser's User-Agent; HTTP uses the same one.
                val userAgent = JsonParser.parseString(bridge.execute("Browser.getVersion", null).get(10, TimeUnit.SECONDS))
                    .asJsonObject.get("userAgent").asString
                return Engine(app, client, ready, bridge, store, terminated, state, StartedBrowser(store, userAgent))
            }
        }
    }

    private class EngineState {
        val closing = AtomicBoolean()
        val windows = ConcurrentHashMap<CefBrowser, SignInWindow>()
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
            window.isVisible = false
            browser.close(true)
        }

        fun closed() {
            SwingUtilities.invokeLater { window.dispose() }
            finished.complete(Unit)
        }
    }

    companion object {
        const val ENGINE_VERSION = "jcef-152.0.6"
    }
}
