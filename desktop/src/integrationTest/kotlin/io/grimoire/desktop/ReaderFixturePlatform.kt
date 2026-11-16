package io.grimoire.desktop

import io.grimoire.api.network.SourceHttpPlatform
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarInputStream

/** Observes app/source calls; unmatched requests fail before any DNS/network access. */
internal class ReaderFixturePlatform : SourceHttpPlatform {
    val allowed = AtomicBoolean(true)
    val requests = AtomicInteger()
    val catalogGate = AtomicReference<CountDownLatch?>()
    override val cookieJar = CookieJar.NO_COOKIES
    override val userAgent = "GrimoireReaderTest/1"
    override val interceptors = listOf(Interceptor { chain ->
        val url = chain.request().url
        check(allowed.get()) { "Unexpected request after offline restart" }
        check(url.host == "reader.invalid" && url.encodedPath in setOf("/catalog", "/details", "/chapters", "/chapter")) {
            "Unexpected fixture URL: $url"
        }
        requests.incrementAndGet()
        if (url.encodedPath == "/catalog") {
            check(catalogGate.get()?.await(5, TimeUnit.SECONDS) != false) { "Fixture catalog gate timed out" }
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
            .message("Fixture").body("".toResponseBody()).build()
    })
}

/** Bind the locally built fixture JAR to the production loader, without putting its classes on the host classpath. */
internal fun readerFixtureManifest(directory: Path): Path {
    val builtJar = Path.of(checkNotNull(System.getProperty("desktop.readerFixtureJar")))
    val bytes = Files.readAllBytes(builtJar)
    val attributes = JarInputStream(bytes.inputStream()).use { it.manifest.mainAttributes }
    val jar = directory.resolve("reader-fixture.jar").toAbsolutePath()
    Files.write(jar, bytes)
    val properties = Properties().apply {
        setProperty("source.file", jar.toString())
        setProperty("source.sha256", MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) })
        for ((field, attribute) in mapOf("package" to "Source-Package", "class" to "Source-Class",
            "versionCode" to "Version-Code", "versionName" to "Version-Name",
            "sourceVersionCode" to "Source-Version-Code", "coreApi" to "Core-Api")) {
            setProperty("source.$field", checkNotNull(attributes.getValue("Grimoire-$attribute")))
        }
    }
    return directory.resolve("fixture.properties").also { path ->
        Files.newOutputStream(path).use { properties.store(it, "Test-only packaged reader fixture") }
    }
}
