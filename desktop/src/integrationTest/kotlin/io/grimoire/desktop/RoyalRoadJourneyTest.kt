package io.grimoire.desktop

import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.network.SourceHttpPlatform
import io.grimoire.api.network.SourceNetwork
import io.grimoire.contract.RoyalRoadContractPlatform
import io.grimoire.reading.encodeChapterContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.Interceptor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RoyalRoadJourneyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun reimportDropsCatalogAndRejectsStaleNovelBeforeHttp() = runBlocking {
        SourceNetwork.install(platform)
        allowed.set(true)
        requests.set(0)
        val model = ReaderModel(LocalLibrary(temporary.newFolder().toPath()), "1.5.0", allowed)
        val manifest = Paths.get(checkNotNull(System.getProperty("desktop.test.manifest")))
        try {
            model.load(manifest)
            model.settled()
            model.browse()
            model.settled()
            val oldNovel = model.state.value.novels.single()
            model.load(manifest)
            model.settled()
            assertEquals(View.LIBRARY, model.state.value.view)
            assertTrue(model.state.value.novels.isEmpty())
            val count = requests.get()
            model.details(oldNovel)
            model.settled(expectError = true)
            assertNotNull(model.state.value.error)
            assertEquals(count, requests.get())
        } finally { model.close() }
    }

    @Test fun packagedSourceReadsThenRestartsOfflineWithoutAnExtension() = runBlocking {
        SourceNetwork.install(platform)
        allowed.set(true)
        requests.set(0)
        val directory = temporary.newFolder().toPath()
        val model = ReaderModel(LocalLibrary(directory), "1.5.0", allowed)
        try {
            model.load(Paths.get(checkNotNull(System.getProperty("desktop.test.manifest"))))
            model.settled()
            assertEquals("Royal Road", model.state.value.sourceName)
            val held = CountDownLatch(1)
            catalogGate.set(held)
            model.browse()
            model.browse() // Duplicate click while claimed busy must not start a second operation.
            held.countDown()
            model.settled()
            assertEquals("The Lantern Keeper", model.state.value.novels.single().title)
            model.details(model.state.value.novels.single())
            model.settled()
            val book = checkNotNull(model.state.value.book)
            assertEquals(2, book.chapters.size)
            val beforeLocked = requests.get()
            model.read(book.chapters.last())
            model.settled(expectError = true)
            assertTrue(model.state.value.error!!.contains("locked"))
            assertEquals(beforeLocked, requests.get())
            model.read(book.chapters.first())
            model.settled()
            val pages = model.state.value.pages
            assertEquals(listOf("The harbor bell rang.", "Mira followed the light."),
                pages.mapNotNull { (it.content as? PageContent.Text)?.text })
            assertTrue(pages.none { (it.content as? PageContent.Text)?.text?.contains("HONEYPOT") == true })
            model.download()
            model.settled()
            model.position(io.grimoire.reading.ChapterKey(book.key, book.chapters.first().url), 2, 19)
            model.close()
            val requestCount = requests.get()
            allowed.set(false) // Any accidental network request now fails, rather than returning fixture data.
            val restarted = ReaderModel(LocalLibrary(directory), "1.5.0", allowed)
            try {
                restarted.offline(true)
                assertNull(restarted.state.value.sourceName) // No JAR or source instance in the restarted host.
                val restored = restarted.state.value.library.single()
                assertEquals(ReadPosition(book.chapters.first().url, 2, 19), restored.position)
                restarted.openSaved(restored)
                restarted.settled()
                restarted.read(restored.chapters.first())
                restarted.settled()
                assertEquals(View.READER, restarted.state.value.view)
                assertEquals(encodeChapterContent(pages), encodeChapterContent(restarted.state.value.pages))
                assertEquals(requestCount, requests.get())
                assertEquals(4, requestCount) // catalog, details, chapter list, chapter
                restarted.browse()
                restarted.settled(expectError = true)
                assertEquals(requestCount, requests.get())
            } finally { restarted.close() }
        } finally { model.close() }
    }

    private suspend fun ReaderModel.settled(expectError: Boolean = false) {
        val value = withTimeout(15_000) { state.first { !it.busy } }
        if (!expectError) assertNull(value.error, value.error)
    }

    companion object {
        private val allowed = AtomicBoolean(true)
        private val requests = AtomicInteger()
        private val catalogGate = AtomicReference<CountDownLatch?>()
        private val fixture = RoyalRoadContractPlatform()
        private val platform = object : SourceHttpPlatform {
            override val cookieJar = fixture.cookieJar
            override val userAgent = fixture.userAgent
            override val interceptors = listOf(Interceptor { chain ->
                requests.incrementAndGet()
                check(allowed.get()) { "Unexpected network request after offline restart" }
                if (chain.request().url.encodedPath == "/fictions/best-rated") {
                    check(catalogGate.get()?.await(5, TimeUnit.SECONDS) != false) { "Fixture catalog gate timed out" }
                }
                fixture.interceptors.single().intercept(chain)
            })
        }
    }
}
