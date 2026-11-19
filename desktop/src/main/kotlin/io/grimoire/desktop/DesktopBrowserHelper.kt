package io.grimoire.desktop

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.grimoire.sessions.SessionCookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs all JCEF work with a profile lease owned by this JVM. */
internal object DesktopBrowserHelper {
    @JvmStatic
    fun main(args: Array<String>) {
        val bootstrap = System.`in`.reader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonArray.map { it.asString }
        }
        val result = Socket(browserLoopback, args.single().toInt()).use { socket ->
            DataOutputStream(socket.getOutputStream()).writeUTF(bootstrap.first())
            serve(BrowserWire(socket), Path.of("").toAbsolutePath(), bootstrap.drop(1))
        }
        // JCEF registers JVM shutdown hooks. Native disposal has already been attempted.
        Runtime.getRuntime().halt(result)
    }

    private fun serve(wire: BrowserWire, directory: Path, args: List<String>): Int {
        val work = Executors.newSingleThreadExecutor { task -> Thread(task, "browser-helper-engine") }
        var engine: DesktopBrowserEngine? = null
        var profileLease: FileLock? = null
        work.execute {
            try {
                profileLease = lockProfile(directory)
                val profile = Files.createDirectories(directory.resolve("profile"))
                val natives = directory.resolve("engine-${DesktopBrowser.ENGINE_VERSION}")
                val native = DesktopBrowserEngine.start(profile, natives, args)
                engine = native
                wire.reply(0, JsonPrimitive(native.userAgent))
            } catch (failure: Exception) {
                runCatching { wire.fail(0, failure) }
            }
        }
        try {
            while (true) {
                val request = wire.read()
                work.execute { engine?.let { handle(wire, it, request, work) } }
            }
        } catch (_: EOFException) {
            // Closing the connection asks for shutdown, including when the reader process crashes.
        } catch (_: IOException) {
            // A broken connection has the same shutdown semantics.
        } finally {
            // The reader also enforces its own deadline. This watchdog covers reader process exit.
            val children = ProcessHandle.current().descendants().use { it.iterator().asSequence().toList() }
            thread(name = "browser-helper-shutdown-watchdog", isDaemon = true) {
                Thread.sleep(15_000)
                children.filter { it.isAlive }.forEach { it.destroyForcibly() }
                ProcessHandle.current().descendants().use { it.forEach(ProcessHandle::destroyForcibly) }
                Runtime.getRuntime().halt(1)
            }
        }

        work.shutdown()
        val clean = work.awaitTermination(5, TimeUnit.SECONDS) &&
            runCatching { engine?.shutdown(Duration.ofSeconds(8)) == true }.getOrDefault(false)
        if (!clean) {
            ProcessHandle.current().descendants().use { it.forEach(ProcessHandle::destroyForcibly) }
            Runtime.getRuntime().halt(1)
        }
        profileLease?.acquiredBy()?.close()
        return 0
    }

    private fun lockProfile(directory: Path): FileLock {
        val channel = FileChannel.open(directory.resolve("profile.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        try {
            val lease = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            if (lease == null) throw IOException("Another Grimoire window is using the browser profile")
            return lease
        } catch (failure: Exception) {
            channel.close()
            throw failure
        }
    }

    private fun handle(
        wire: BrowserWire,
        browser: DesktopBrowserEngine,
        request: JsonArray,
        work: java.util.concurrent.Executor,
    ) {
        val id = request[0].asLong
        try {
            when (request[1].asString) {
                "signIn" -> {
                    val finished = CompletableFuture<Unit>()
                    val successUrl = request[4].takeUnless { it.isJsonNull }?.asString
                    browser.openWindow(request[2].asString, request[3].asString, successUrl, finished)
                    finished.thenRunAsync({
                        try {
                            browser.store.flush()
                            wire.reply(id)
                        } catch (failure: Exception) {
                            runCatching { wire.fail(id, failure) }
                        }
                    }, work)
                }
                "cookies" -> {
                    val cookies = browser.store.cookiesForRequest(request[2].asString.toHttpUrl())
                    wire.reply(id, browserJson.toJsonTree(cookies.map { SessionCookie.fromCookie(it) }))
                }
                "setCookie" -> {
                    browser.store.setCookie(request[2].asString.toHttpUrl(), request[3].asString)
                    wire.reply(id)
                }
                "snapshot" -> wire.reply(id, browserJson.toJsonTree(browser.store.snapshot(request[2].asString.toHttpUrl())))
                "flush" -> {
                    browser.store.flush()
                    wire.reply(id)
                }
                else -> throw IOException("Unknown browser operation")
            }
        } catch (failure: Exception) {
            runCatching { wire.fail(id, failure) }
        }
    }
}
