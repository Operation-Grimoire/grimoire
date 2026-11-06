package io.grimoire.app.extension.repo

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublicReleaseDownloadTest {
    @get:Rule val temporary = TemporaryFolder()

    private val base = "https://github.com/owner/repo/releases/download/latest"
    private val api = "https://api.github.com/repos/owner/repo/releases"
    private val index = """[{"name":"Source","pkg":"source.pkg","lang":"en","baseUrl":"https://source.test","versionCode":1,"versionName":"1","apk":"source.apk","url":"$base/source.apk","iconUrl":"$base/icon.png"}]"""
    private val release = """{"assets":[{"id":1,"name":"index.json"},{"id":2,"name":"source.apk"},{"id":3,"name":"icon.png"}]}"""

    private fun client(routes: Map<String, Pair<Int, String>>, requests: MutableList<String>) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            requests.add(url)
            val (code, body) = routes[url] ?: error("Unexpected request: $url")
            if (url.contains("/releases/assets/")) {
                assertEquals("application/octet-stream", request.header("Accept"))
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()

    @Test fun publicIndexKeepsDirectUrlsAndCachesThem() = runBlocking {
        val requests = mutableListOf<String>()
        val fetcher = ExtensionIndexFetcher(temporary.newFolder(), client(
            mapOf("$base/index.json" to (200 to index)), requests,
        ))
        val entry = fetcher.fetch("$base/index.json").getOrThrow().single()
        assertEquals("$base/source.apk", entry.url)
        assertEquals("$base/icon.png", entry.iconUrl)
        assertEquals(listOf("$base/index.json"), requests)
        assertEquals(listOf(entry), fetcher.loadCached("$base/index.json"))
    }

    @Test fun privateIndexFallsBackToApiAndRewritesAssets() = runBlocking {
        for (status in listOf(401, 404)) {
            val requests = mutableListOf<String>()
            val fetcher = ExtensionIndexFetcher(temporary.newFolder(), client(mapOf(
                "$base/index.json" to (status to ""),
                "$api/tags/latest" to (200 to release),
                "$api/assets/1" to (200 to index),
            ), requests))
            val entry = fetcher.fetch("$base/index.json").getOrThrow().single()
            assertEquals("$api/assets/2", entry.url)
            assertEquals("$api/assets/3", entry.iconUrl)
            assertEquals(listOf("$base/index.json", "$api/tags/latest", "$api/assets/1"), requests)
        }
    }

    @Test fun publicApkDownloadsWithoutApiLookupAndChecksHash() = runBlocking {
        val requests = mutableListOf<String>()
        val installer = ExtensionInstaller(temporary.newFolder(), client(
            mapOf("$base/source.apk" to (200 to "abc")), requests,
        )) { "HTTP $it" }
        val file = installer.download("$base/source.apk", "source.pkg",
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        ).getOrThrow()
        assertEquals("abc", file.readText())
        assertEquals(listOf("$base/source.apk"), requests)
        assertTrue(installer.download("$base/source.apk", "source.pkg", "wrong")
            .exceptionOrNull() is HashMismatchException)
        assertFalse(file.exists())
    }

    @Test fun privateApkFallsBackToApi() = runBlocking {
        val requests = mutableListOf<String>()
        val installer = ExtensionInstaller(temporary.newFolder(), client(mapOf(
            "$base/source.apk" to (404 to ""),
            "$api/tags/latest" to (200 to release),
            "$api/assets/2" to (200 to "apk bytes"),
        ), requests)) { "HTTP $it" }
        assertEquals("apk bytes", installer.download("$base/source.apk", "source.pkg")
            .getOrThrow().readText())
        assertEquals(listOf("$base/source.apk", "$api/tags/latest", "$api/assets/2"), requests)
    }

    @Test fun serverAndThrottleFailuresDoNotTriggerApiFallback() = runBlocking {
        for (status in listOf(403, 429, 500)) {
            val requests = mutableListOf<String>()
            val http = client(mapOf("$base/index.json" to (status to ""),
                "$base/source.apk" to (status to "")), requests)
            assertTrue(ExtensionIndexFetcher(temporary.newFolder(), http)
                .fetch("$base/index.json").isFailure)
            assertTrue(ExtensionInstaller(temporary.newFolder(), http) { "HTTP $it" }
                .download("$base/source.apk", "source.pkg").isFailure)
            assertEquals(listOf("$base/index.json", "$base/source.apk"), requests)
        }
    }
}
