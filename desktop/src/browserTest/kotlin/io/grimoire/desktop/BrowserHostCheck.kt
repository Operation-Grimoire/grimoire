package io.grimoire.desktop

import io.grimoire.sessions.SameSite
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Native check of the Desktop browser host with real embedded Chromium against a local HTTPS
 * server. `session` signs in and writes cookies, `restart` checks persistence and `recovery`
 * terminates the helper during sign-in. Each phase checks that the helper stops.
 */
fun main(args: Array<String>) {
    val phase = args.getOrNull(0) ?: "session"
    val directory = Path.of(args.getOrNull(1) ?: "build/browser-check").toAbsolutePath()
    // A native hang never reaches the catch below; dump where it is stuck, then fail.
    Thread({
        Thread.sleep(150_000)
        System.err.println("WATCHDOG: phase $phase still running after 150 s")
        dumpThreads()
        Runtime.getRuntime().halt(2)
    }, "browser-check-watchdog").apply { isDaemon = true }.start()
    try {
        runPhase(phase, directory)
        println("PASS: desktop browser $phase")
        exitProcess(0)
    } catch (failure: Throwable) {
        failure.printStackTrace()
        dumpThreads()
        exitProcess(1)
    }
}

private fun dumpThreads() = Thread.getAllStackTraces().forEach { (thread, stack) ->
    System.err.println("Thread ${thread.name}: ${thread.state}")
    stack.forEach { System.err.println("  at $it") }
}

private fun runPhase(phase: String, directory: Path) {
    Files.createDirectories(directory)
    if (phase == "session") {
        // Fresh profile; the downloaded engine is kept between runs.
        directory.resolve("browser/profile").toFile().deleteRecursively()
    }
    // One certificate for both phases so the SPKI pin and the cookie host stay the same.
    val certificate = certificate(directory.resolve("fixture.p12"))
    val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
    val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
    val spki = java.util.Base64.getEncoder().encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest(certificate.certificate.publicKey.encoded))
    val network = AtomicBoolean(true)
    // CI can try extra engine switches per OS, e.g. JCEF_EXTRA_ARGS="--no-sandbox --disable-gpu".
    val extra = System.getenv("JCEF_EXTRA_ARGS").orEmpty().split(' ').filter { it.isNotBlank() }
    val browser = DesktopBrowser(directory.resolve("browser"), network, listOf("--ignore-certificate-errors-spki-list=$spki") + extra)
    val platform = DesktopHttpPlatform(network, browser)
    val userAgents = mutableListOf<String?>()
    val waitingForSignIn = CompletableFuture<Unit>()
    MockWebServer().use { server ->
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(userAgents) { userAgents += request.getHeader("User-Agent") }
                return when (request.path?.substringBefore('?')) {
                    "/login" -> MockResponse().setBody("<html><script>location.replace('/session')</script>Signing in</html>")
                    "/wait" -> {
                        waitingForSignIn.complete(Unit)
                        MockResponse().setBody("Waiting for sign-in")
                    }
                    "/session" -> MockResponse().setResponseCode(302).addHeader("Location", "/private")
                        .addHeader("Set-Cookie", "login=browser; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=3600")
                    "/private" -> if (request.getHeader("Cookie")?.contains("login=") == true) MockResponse().setBody("Private chapter")
                        else MockResponse().setResponseCode(401)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        fun url(path: String) = server.url(path).newBuilder().host("127.0.0.1").build()
        val http = OkHttpClient.Builder()
            .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .cookieJar(platform.cookieJar)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", platform.userAgent).build()) }
            .apply { platform.interceptors.forEach(::addInterceptor); platform.networkInterceptors.forEach(::addNetworkInterceptor) }
            .build()
        fun fetch(path: String) = http.newCall(Request.Builder().url(url(path)).build()).execute().use { it.code }
        val cookies = platform.cookies
        val recovering = phase == "recovery"
        val shutdown: BrowserShutdown
        try {
            when (phase) {
                "session" -> {
                    check(!browser.profileInUse) { "Expected a fresh profile" }
                    check(fetch("/private") == 401)
                    check(!browser.running) { "Plain HTTP started Chromium" }
                    // The sign-in window follows /login -> /session -> /private and closes on the success URL.
                    val signedIn = CompletableFuture.runAsync { browser.signIn("Sign in to fixture", url("/login").toString(), url("/private").toString()) }
                    signedIn.get(60, TimeUnit.SECONDS)
                    check(browser.running && browser.profileInUse)
                    check(fetch("/private") == 200) { "HTTP did not use the browser sign-in" }
                    val agent = synchronized(userAgents) { userAgents.last() }
                    check(agent != null && agent.contains("Chrome/")) { "HTTP did not use the browser User-Agent: $agent" }
                    check(cookies.cookieHeader(url("/private")).contains("login=browser"))
                    val login = browser.session().store.snapshot(url("/private")).single { it.name == "login" }
                    check(login.hostOnly && login.secure && login.httpOnly && login.sameSite == SameSite.LAX)
                    // Source logout: expire through SourceCookies, as Webnovel does.
                    cookies.setCookie(url("/"), "login=; Max-Age=0; Path=/; Secure")
                    check(fetch("/private") == 401) { "Logout did not reach HTTP" }
                    cookies.setCookie(url("/"), "remember=yes; Path=/; Secure; Max-Age=3600")
                    cookies.flush()
                    // A second host on the same profile is refused while this one runs.
                    val second = DesktopBrowser(directory.resolve("browser"), network)
                    val refused = try { runCatching { second.session() }.exceptionOrNull() }
                    finally { second.close() }
                    check(refused?.message == "Another Grimoire window is using the browser profile") { "Second host: $refused" }
                }
                "restart" -> {
                    check(browser.profileInUse) { "Profile missing after restart" }
                    val header = cookies.cookieHeader(url("/private"))
                    check(header.contains("remember=yes")) { "Persistent cookie lost: $header" }
                    check(!header.contains("login=")) { "Logout did not persist: $header" }
                    check(fetch("/private") == 401)
                }
                "recovery" -> {
                    val page = url("/private")
                    check(cookies.cookieHeader(page).contains("remember=yes"))
                    val originalHelper = ProcessHandle.of(checkNotNull(browser.helperPid)).orElseThrow()
                    val signIn = CompletableFuture.runAsync { browser.signIn("Waiting for sign-in", url("/wait").toString(), null) }
                    waitingForSignIn.get(30, TimeUnit.SECONDS)
                    check(!signIn.isDone)
                    check(originalHelper.destroyForcibly())
                    originalHelper.onExit().get(8, TimeUnit.SECONDS)
                    val failure = runCatching { signIn.get(8, TimeUnit.SECONDS) }.exceptionOrNull()
                    check(failure is ExecutionException && failure.cause is java.io.IOException) { "Unexpected sign-in result: $failure" }

                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
                    while (true) {
                        try {
                            check(cookies.cookieHeader(page).contains("remember=yes")) { "Cookie lost after termination" }
                            break
                        } catch (stopping: java.io.IOException) {
                            if (stopping.message != "The browser helper is stopping. Try again." || System.nanoTime() >= deadline) throw stopping
                            Thread.sleep(50)
                        }
                    }
                    check(browser.helperPid != originalHelper.pid())
                    check(fetch("/private") == 401)
                    println("Reader survived helper termination and reused the profile")
                }
                else -> error("Unknown phase $phase")
            }
        } finally {
            val started = System.nanoTime()
            shutdown = browser.close(Duration.ofSeconds(if (recovering) 4 else 20))
            println("shutdown $shutdown in ${(System.nanoTime() - started) / 1_000_000} ms")
        }
        check(shutdown != BrowserShutdown.INCOMPLETE) { "Browser helper outlived its shutdown deadline" }
        if (recovering) check(shutdown == BrowserShutdown.FORCED) { "The forced cleanup path did not run" }
    }
}

private fun certificate(file: Path): HeldCertificate {
    val password = "fixture".toCharArray()
    if (Files.exists(file)) {
        val store = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(file).use { load(it, password) } }
        val key = store.getKey("fixture", password) as java.security.PrivateKey
        val cert = store.getCertificate("fixture") as java.security.cert.X509Certificate
        return HeldCertificate(KeyPair(cert.publicKey, key), cert)
    }
    val held = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").duration(1, TimeUnit.DAYS).build()
    val store = KeyStore.getInstance("PKCS12").apply { load(null, null) }
    store.setKeyEntry("fixture", held.keyPair.private, password, arrayOf(held.certificate))
    Files.newOutputStream(file).use { store.store(it, password) }
    return held
}
