package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.ChapterLoader
import io.grimoire.reading.NovelKey
import io.grimoire.reading.storage.DownloadRepository
import io.grimoire.reading.storage.LibraryDao
import io.grimoire.reading.storage.LibraryEntry
import io.grimoire.reading.storage.LibraryRepository
import io.grimoire.reading.storage.SavedChapterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class LibraryStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val key = NovelKey(17, "/book")
    private val novel = Novel(key.url, "Source title", Language.EN, author = "Author", description = "Description")
    private val chapter = Chapter("/chapter", "Chapter")

    @Test fun membershipUsesCurrentMetadataAndRetainsDownloadsAndPendingPositionAcrossRestart() = runBlocking {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory)
        for (source in listOf(17L, 18L)) library.remember(NovelKey(source, key.url), novel, listOf(chapter))
        val pages = listOf(NovelPage(0, PageContent.Text("Offline prose")))
        library.download(ChapterKey(key, chapter.url), pages)
        val before = library.readSnapshot(ChapterKey(key, chapter.url))
        val repository = LibraryRepository(DesktopLibraryDao(library))
        library.remember(key, novel.copy(title = "Refreshed title"), listOf(chapter))
        library.position(ChapterKey(key, chapter.url), 0, 17)
        for (value in listOf(true, true, false, false)) {
            val entry = repository.setMembership(key, value)!!
            assertEquals("Refreshed title", entry.title)
            assertEquals("Author", entry.author)
            assertEquals("Description", entry.description)
            assertEquals(value, entry.inLibrary)
        }
        assertFalse(repository.find(NovelKey(18, key.url))!!.inLibrary)
        val metadata = Files.readAllBytes(directory.resolve("library.json"))
        for (missing in listOf(NovelKey(19, key.url), key.copy(url = "/missing"))) {
            assertNull(repository.find(missing))
            assertNull(repository.setMembership(missing, true))
            assertArrayEquals(metadata, Files.readAllBytes(directory.resolve("library.json")))
        }
        library.close()
        val restarted = LocalLibrary(directory)
        assertEquals(ReadPosition(chapter.url, 0, 17), restarted.book(key)!!.position)
        assertEquals(pages, restarted.downloaded(ChapterKey(key, chapter.url)))
        assertTrue(restarted.saved().isEmpty())
        assertEquals(before, restarted.readSnapshot(ChapterKey(key, chapter.url)))
        restarted.close()
    }

    @Test fun readerUsesCommittedMetadataAndSurfacesMembershipWriteFailure() = runBlocking {
        val directory = temporary.newFolder().toPath()
        var library = LocalLibrary(directory)
        library.remember(key, novel, listOf(chapter))
        val model = desktopReaderModel(library, "1.5.0", AtomicBoolean(false))
        try {
            model.openSaved(library.book(key)!!)
            withTimeout(5_000) { model.state.first { !it.busy } }
            library.remember(key, novel.copy(title = "New metadata"), listOf(chapter))
            model.favorite()
            val state = withTimeout(5_000) { model.state.first { !it.busy } }
            assertNull(state.error)
            assertEquals("New metadata", state.book!!.novel.title)
            assertTrue(state.book!!.favorite)
        } finally { model.close() }

        library = LocalLibrary(directory)
        var writes = 0
        val failing = LibraryRepository(object : LibraryDao {
            override suspend fun find(key: NovelKey): LibraryEntry? = error("Unused")
            override suspend fun setMembership(requested: NovelKey, inLibrary: Boolean): LibraryEntry? {
                assertEquals(key, requested)
                assertFalse(inLibrary)
                writes++
                throw IOException("membership disk failure")
            }
        })
        val failedModel = ReaderModel(library, "1.5.0", AtomicBoolean(false),
            ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(library))), failing, DownloadRepository(DesktopDownloadDao(library)))
        try {
            failedModel.openSaved(library.book(key)!!)
            withTimeout(5_000) { failedModel.state.first { !it.busy } }
            failedModel.favorite()
            val state = withTimeout(5_000) { failedModel.state.first { !it.busy } }
            assertEquals("membership disk failure", state.error)
            assertTrue(state.book!!.favorite)
            assertTrue(library.book(key)!!.favorite)
            assertEquals(1, writes)
        } finally { failedModel.close() }
    }
}
