package io.grimoire.desktop

import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.network.SourceNetwork
import io.grimoire.reading.encodeChapterContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.storage.*
import io.grimoire.api.model.novel.NovelPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReaderJourneyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sourceSnapshotKeepsItsEarlierAbsenceWhileAConcurrentSaveCommits() = runBlocking {
        SourceNetwork.install(platform)
        allowed.set(true); requests.set(0)
        val library = LocalLibrary(temporary.newFolder().toPath())
        val model = desktopReaderModel(library, "1.6.0", allowed)
        val gate = CountDownLatch(1)
        val entered = CountDownLatch(1)
        try {
            model.load(readerFixtureManifest(temporary.newFolder().toPath())); model.settled()
            model.browse(); model.settled()
            model.details(model.state.value.novels.single()); model.settled()
            val book = requireNotNull(model.state.value.book)
            val chapter = book.chapters.first()
            val key = ChapterKey(book.key, chapter.url)
            platform.chapterGate.set(gate); platform.chapterEntered.set(entered)
            model.read(chapter)
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            val downloads = DownloadRepository(DesktopDownloadDao(library))
            assertTrue(downloads.commit(requireNotNull(downloads.claim(key)),
                listOf(NovelPage(0, PageContent.Text("New stored body")))) is DownloadCommit.Committed)
            gate.countDown(); model.settled()
            val old = requireNotNull(model.state.value.storageObservation)
            val adapter = DesktopSavedChapterDao(library)
            assertEquals(ChapterContentPresence.ABSENT, old.presence)
            assertFalse(adapter.isObservationCurrent(old))
            assertNotEquals("New stored body", (model.state.value.pages.first().content as PageContent.Text).text)
            model.read(chapter); model.settled()
            val fresh = requireNotNull(model.state.value.storageObservation)
            assertEquals(ChapterContentPresence.SAVED, fresh.presence)
            assertTrue(adapter.isObservationCurrent(fresh))
            assertEquals("New stored body", (model.state.value.pages.single().content as PageContent.Text).text)
        } finally {
            gate.countDown(); platform.chapterGate.set(null); platform.chapterEntered.set(null)
            model.close()
        }
    }

    @Test fun reimportDropsCatalogAndRejectsStaleNovelBeforeHttp() = runBlocking {
        SourceNetwork.install(platform)
        allowed.set(true)
        requests.set(0)
        val model = desktopReaderModel(LocalLibrary(temporary.newFolder().toPath()), "1.5.0", allowed)
        val manifest = readerFixtureManifest(temporary.newFolder().toPath())
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
        val model = desktopReaderModel(LocalLibrary(directory), "1.5.0", allowed)
        try {
            model.load(readerFixtureManifest(temporary.newFolder().toPath()))
            model.settled()
            assertEquals("Reader fixture", model.state.value.sourceName)
            val held = CountDownLatch(1)
            catalogGate.set(held)
            model.browse()
            model.browse() // Duplicate click while claimed busy must not start a second operation.
            held.countDown()
            model.settled()
            assertEquals("A fixture novel", model.state.value.novels.single().title)
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
            assertEquals(3, pages.size)
            assertTrue(pages.any { it.content is PageContent.Text })
            assertTrue(pages.any { it.content is PageContent.Separator })
            model.download()
            model.settled()
            model.position(io.grimoire.reading.ChapterKey(book.key, book.chapters.first().url), 2, 19, model.state.value.projection!!)
            model.browse()
            model.settled()
            model.close()
            LocalLibrary(directory).use { assertEquals(ReadPosition(book.chapters.first().url, 2, 19), it.saved().single().position) }
            val requestCount = requests.get()
            allowed.set(false) // Any accidental network request now fails, rather than returning fixture data.
            val restarted = desktopReaderModel(LocalLibrary(directory), "1.5.0", allowed)
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
                assertEquals(5, requestCount) // two catalog visits, details, chapter list, chapter
                restarted.browse()
                restarted.settled(expectError = true)
                assertTrue(restarted.state.value.error!!.contains("offline"))
                assertEquals(requestCount, requests.get())
            } finally { restarted.close() }
        } finally { model.close() }
    }

    private suspend fun ReaderModel.settled(expectError: Boolean = false) {
        val value = withTimeout(15_000) { state.first { !it.busy } }
        if (expectError) assertNotNull("Expected an error", value.error) else assertNull(value.error, value.error)
    }

    companion object {
        private val platform = ReaderFixturePlatform()
        private val allowed = platform.allowed
        private val requests = platform.requests
        private val catalogGate = platform.catalogGate
    }
}
