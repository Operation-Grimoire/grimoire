package io.grimoire.desktop

import com.google.gson.JsonElement
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

enum class BrowserShutdown { GRACEFUL, FORCED, INCOMPLETE }

internal class BrowserHelperProcess private constructor(
    private val process: Process,
    private val listener: ServerSocket,
    private val token: String,
    private val argsFile: Path,
) {
    private val closing = AtomicBoolean()
    private val ready = CompletableFuture<StartedBrowser>()
    private val requests = ConcurrentHashMap<Long, CompletableFuture<JsonElement>>()
    private val requestNumber = AtomicLong()
    private val children = ConcurrentHashMap.newKeySet<ProcessHandle>()
    private val stopped = CountDownLatch(1)
    @Volatile private var socket: Socket? = null
    @Volatile private var wire: BrowserWire? = null

    val pid: Long get() = process.pid()
    val alive: Boolean get() = process.isAlive && !closing.get()
    val running: Boolean get() = alive && ready.isDone && !ready.isCompletedExceptionally
    val canReplace: Boolean get() = stopped.count == 0L && children.none { it.isAlive }

    init {
        thread(name = "browser-helper-responses", isDaemon = true) { receive() }
        thread(name = "browser-helper-exit", isDaemon = true) { observeExit() }
    }

    fun session(): StartedBrowser = await(ready, Duration.ofMinutes(2))

    fun request(operation: String, vararg args: String?, timeout: Duration? = Duration.ofSeconds(15)): JsonElement {
        val response = CompletableFuture<JsonElement>()
        val id = requestNumber.incrementAndGet()
        requests[id] = response
        try {
            val connection = wire?.takeIf { alive } ?: throw IOException("The browser helper stopped")
            connection.write(browserRequest(id, operation, args))
            return await(response, timeout)
        } finally {
            requests.remove(id)
        }
    }

    private fun <T> await(reply: CompletableFuture<T>, timeout: Duration?): T = try {
        if (timeout == null) reply.get() else reply.get(timeout.toNanos(), TimeUnit.NANOSECONDS)
    } catch (failure: ExecutionException) {
        throw IOException(failure.cause?.message ?: "The browser helper stopped", failure.cause)
    } catch (failure: TimeoutException) {
        disconnect()
        throw IOException("The browser helper did not respond. Try again.", failure)
    }

    private fun receive() {
        try {
            accept().use { peer ->
                socket = peer
                if (closing.get()) return
                val connection = BrowserWire(peer)
                wire = connection
                while (true) {
                    val response = connection.read()
                    val id = response[0].asLong
                    val data = response[2]
                    if (id == 0L) {
                        if (response[1].asBoolean) ready.complete(StartedBrowser(HelperCookieStore(this), data.asString))
                        else throw IOException(data.asString)
                    } else {
                        val reply = requests[id] ?: continue
                        if (response[1].asBoolean) reply.complete(data)
                        else reply.completeExceptionally(IOException(data.asString))
                    }
                }
            }
        } catch (failure: Exception) {
            val error = if (closing.get() || failure is java.io.EOFException) {
                IOException("The browser helper stopped")
            } else failure
            disconnect(error)
        } finally {
            disconnect()
            runCatching { Files.deleteIfExists(argsFile) }
        }
    }

    private fun accept(): Socket {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        listener.soTimeout = 500
        while (alive && System.nanoTime() < deadline) {
            val peer = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
            try {
                peer.soTimeout = 500
                if (DataInputStream(peer.getInputStream()).readUTF() == token) {
                    peer.soTimeout = 0
                    listener.close()
                    return peer
                }
            } catch (_: IOException) {
                // Only the spawned JVM has the token sent through its standard input.
            }
            peer.close()
        }
        throw IOException("Could not connect to the browser helper")
    }

    private fun rememberChildren() {
        process.descendants().use { it.forEach(children::add) }
    }

    private fun observeExit() {
        try {
            while (process.isAlive) {
                rememberChildren()
                children.removeIf { !it.isAlive }
                process.waitFor(100, TimeUnit.MILLISECONDS)
            }
            disconnect()
            children.filter { it.isAlive }.forEach { it.destroyForcibly() }
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (children.any { it.isAlive } && System.nanoTime() < until) Thread.sleep(25)
        } finally {
            stopped.countDown()
        }
    }

    private fun disconnect(error: Exception = IOException("The browser helper stopped")) {
        closing.set(true)
        runCatching { listener.close() }
        runCatching { socket?.close() }
        ready.completeExceptionally(error)
        requests.values.forEach { it.completeExceptionally(error) }
    }

    fun close(deadline: Duration): BrowserShutdown {
        val until = System.nanoTime() + deadline.toNanos()
        disconnect()
        // Reserve time for forced cleanup even when native shutdown hangs.
        val grace = (deadline.toNanos() - TimeUnit.SECONDS.toNanos(4)).coerceAtLeast(0)
        val forced = !process.waitFor(grace, TimeUnit.NANOSECONDS)
        if (forced) {
            rememberChildren()
            process.destroyForcibly()
        }
        if (!stopped.await((until - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS) || !canReplace) {
            return BrowserShutdown.INCOMPLETE
        }
        return if (forced || process.exitValue() != 0) BrowserShutdown.FORCED else BrowserShutdown.GRACEFUL
    }

    companion object {
        fun launch(directory: Path, engineArgs: List<String>): BrowserHelperProcess {
            Files.createDirectories(directory)
            val listener = ServerSocket()
            val token = UUID.randomUUID().toString()
            val file = Files.createTempFile("grimoire-browser-", ".args")
            var process: Process? = null
            try {
                listener.bind(InetSocketAddress(browserLoopback, 0))
                val javaName = if (DesktopBrowser.windows) "java.exe" else "java"
                val java = Path.of(System.getProperty("java.home"), "bin", javaName)
                val opens = listOf("sun.awt", "sun.lwawt", "sun.lwawt.macosx", "java.awt.peer")
                    .map { "--add-opens=java.desktop/$it=ALL-UNNAMED" }
                val args = opens + listOf(
                    "-XX:ErrorFile=${directory.toAbsolutePath().resolve("hs_err_pid%p.log")}",
                    "-cp", System.getProperty("java.class.path"), DesktopBrowserHelper::class.java.name,
                    listener.localPort.toString(), directory.toAbsolutePath().toString(),
                ) + engineArgs
                // Java argument files keep the command below Windows' command-line length limit.
                Files.writeString(file, args.joinToString("\n") {
                    "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                })
                process = ProcessBuilder(java.toString(), "-Dgrimoire.browser.helper=true", "@$file")
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                DataOutputStream(process.outputStream).use { it.writeUTF(token) }
                return BrowserHelperProcess(process, listener, token, file)
            } catch (failure: Exception) {
                process?.destroyForcibly()
                runCatching { listener.close() }
                runCatching { Files.deleteIfExists(file) }
                throw failure
            }
        }
    }
}
