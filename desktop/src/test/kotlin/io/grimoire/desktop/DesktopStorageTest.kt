package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.storage.SavedChapterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicBoolean

class DesktopStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val novel = Novel("/book", "Book", Language.EN)
    private val chapter = Chapter("/chapter", "Chapter")
    private val key = ChapterKey(NovelKey(17, novel.url), chapter.url)
    private val pages = listOf(NovelPage(0, PageContent.Text("Saved prose", "<b>Saved prose</b>")))

    @Test fun restartKeepsContentAndPositionAfterLibraryRemovalAndSeparatesSourceIdentities() = runBlocking {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory)
        for (source in listOf(17L, 18L)) {
            val otherKey = key.copy(novel = NovelKey(source, novel.url))
            library.remember(otherKey.novel, novel, listOf(chapter))
            library.download(otherKey, listOf(NovelPage(0, PageContent.Text("Source $source"))))
        }
        library.favorite(key.novel, true)
        library.position(key, 0, 17)
        library.favorite(key.novel, false)
        val restarted = LocalLibrary(directory)
        val repository = SavedChapterRepository(FileSavedChapterDao(restarted))
        assertTrue(restarted.saved().isEmpty())
        assertEquals(ReadPosition(chapter.url, 0, 17), restarted.book(key.novel)!!.position)
        for (source in listOf(17L, 18L)) {
            assertEquals(PageContent.Text("Source $source"),
                repository.read(key.copy(novel = NovelKey(source, novel.url)))!!.single().content)
        }
        assertNull(repository.read(key.copy(novel = NovelKey(19, novel.url))))
        assertNull(repository.read(key.copy(url = "/missing")))
    }

    @Test fun adapterPreservesLegacyBytesAndRejectsCorruptionAndMissingIndexedFiles() = runBlocking {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory)
        library.remember(key.novel, novel, listOf(chapter))
        library.download(key, pages)
        val file = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("chapter-") }.findFirst().get() }
        val legacy = "Legacy\u001c<i>Legacy</i>"
        val record = JSONObject(Files.readString(file)).put("schema", 1).put("content", legacy)
            .put("sha256", sha256(legacy.toByteArray(Charsets.UTF_8)))
        record.remove("contentVersion")
        Files.writeString(file, record.toString())
        val before = Files.readAllBytes(file)
        val repository = SavedChapterRepository(FileSavedChapterDao(LocalLibrary(directory)))
        assertEquals(PageContent.Text("Legacy", "<i>Legacy</i>"), repository.read(key)!!.single().content)
        assertArrayEquals(before, Files.readAllBytes(file))
        library.remember(key.novel, novel, listOf(chapter.copy(locked = true)))
        try {
            SavedChapterRepository(FileSavedChapterDao(LocalLibrary(directory))).read(key)
            fail("Locked chapter was read")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, Files.readAllBytes(file))
        library.remember(key.novel, novel, listOf(chapter))
        for (invalid in listOf(JSONObject(record.toString()).put("sha256", "wrong").toString(),
            JSONObject(record.toString()).put("sourceId", 18).toString(),
            JSONObject(record.toString()).put("schema", 99).toString())) {
            Files.writeString(file, invalid)
            try { repository.read(key); fail("Invalid content was accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(invalid, Files.readString(file))
        }
        Files.delete(file)
        try { repository.read(key); fail("An indexed missing file was treated as no download") }
        catch (_: NoSuchFileException) { }
    }

    @Test fun readerSurfacesStorageFailureInsteadOfTryingTheSource() = runBlocking {
        val library = LocalLibrary(temporary.newFolder().toPath())
        library.remember(key.novel, novel, listOf(chapter))
        val repository = SavedChapterRepository { throw IOException("saved chapter disk failure") }
        val model = ReaderModel(library, "1.5.0", AtomicBoolean(false), repository)
        try {
            model.openSaved(library.book(key.novel)!!)
            withTimeout(5_000) { model.state.first { !it.busy } }
            model.read(chapter)
            val state = withTimeout(5_000) { model.state.first { !it.busy } }
            assertEquals("saved chapter disk failure", state.error)
            assertTrue(state.pages.isEmpty())
        } finally { model.close() }
    }
}
