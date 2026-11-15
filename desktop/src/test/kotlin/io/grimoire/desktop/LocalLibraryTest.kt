package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.*
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import org.json.JSONObject

class LocalLibraryTest {
    @Test fun legacyChapterReadsWithoutRewritingBytesAndRetainsPosition() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val chapter = ChapterKey(key, "/chapter")
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        library.favorite(key, true)
        library.download(chapter, pages)
        library.position(chapter, 0, 17)
        library.flush()
        val file = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("chapter-") }.findFirst().get() }
        val legacy = "Legacy prose\u001c<i>Legacy prose</i>\u001f\u001d\u001f\u001ehttps://example.org/i"
        val record = JSONObject(Files.readString(file)).put("schema", 1).put("content", legacy)
            .put("sha256", sha256(legacy.toByteArray(Charsets.UTF_8)))
        record.remove("contentVersion")
        Files.writeString(file, record.toString())
        val before = Files.readAllBytes(file)
        val restarted = LocalLibrary(directory)
        val actual = restarted.downloaded(chapter)!!
        assertEquals(PageContent.Text("Legacy prose", "<i>Legacy prose</i>"), actual[0].content)
        assertTrue(actual[1].content is PageContent.Separator)
        assertEquals(PageContent.Image("https://example.org/i"), actual[2].content)
        assertEquals(ReadPosition("/chapter", 0, 17), restarted.saved().single().position)
        assertArrayEquals(before, Files.readAllBytes(file))
    }

    @Test fun versionedControlsSurviveRestartAndUnsupportedMetadataIsNotRewritten() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val chapter = ChapterKey(key, "/chapter")
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        val text = "Prose\u0000\u001c\u001d\u001e\u001f中文😀"
        val expected = listOf(NovelPage(0, PageContent.Text(text, "<b>$text</b>")),
            NovelPage(1, PageContent.Image("https://example.org/\u001c\u001d\u001e\u001f")))
        library.download(chapter, expected)
        val file = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("chapter-") }.findFirst().get() }
        assertEquals(expected, LocalLibrary(directory).downloaded(chapter))
        for (version in listOf(1, 99, "2", 2.5, 4_294_967_298L)) {
            val changed = JSONObject(Files.readString(file)).put("contentVersion", version).toString()
            Files.writeString(file, changed)
            try { LocalLibrary(directory).downloaded(chapter); fail("Accepted incompatible format") }
            catch (_: IllegalArgumentException) { }
            assertEquals(changed, Files.readString(file))
        }
    }

    @get:Rule val temporary = TemporaryFolder()
    private val novel = Novel("/novel", "A public novel", Language.EN)
    private val chapters = listOf(Chapter("/chapter", "First"), Chapter("/locked", "Locked", locked = true))
    private val pages = listOf(NovelPage(0, PageContent.Text("Faithful prose.", "<b>Faithful</b> prose.")))

    @Test fun restartRestoresLibraryPositionAndRichContentWithoutAnExtension() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val chapter = ChapterKey(key, chapters.first().url)
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        library.favorite(key, true)
        library.download(chapter, pages)
        library.position(chapter, 0, 37)
        library.flush()
        val restarted = LocalLibrary(directory)
        assertEquals(ReadPosition(chapters.first().url, 0, 37), restarted.saved().single().position)
        assertEquals(pages, restarted.downloaded(chapter))
        assertNull(restarted.downloaded(ChapterKey(NovelKey(18, novel.url), chapters.first().url)))
        assertNull(restarted.downloaded(ChapterKey(key, "/other")))
    }

    @Test fun lockedChapterCannotBeDownloadedAndDoesNotMutateStorage() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        val before = Files.readString(directory.resolve("library.json"))
        try { library.download(ChapterKey(key, "/locked"), pages); fail("Locked chapter was saved") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, Files.readString(directory.resolve("library.json")))
        assertEquals(1, Files.list(directory).use { it.count() }.toInt())
    }

    @Test fun corruptDownloadFailsExplicitlyWithoutFetchingOrReplacingIt() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val chapter = ChapterKey(key, "/chapter")
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        library.download(chapter, pages)
        val file = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("chapter-") }.findFirst().get() }
        val damaged = Files.readString(file).replace("Faithful prose.", "Changed prose.")
        Files.writeString(file, damaged)
        try { LocalLibrary(directory).downloaded(chapter); fail("Corrupt content was accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(damaged, Files.readString(file))
    }

    @Test fun unreadableLibraryIsNotSilentlyReset() {
        val directory = temporary.newFolder().toPath()
        Files.writeString(directory.resolve("library.json"), "{broken")
        try { LocalLibrary(directory); fail("Corrupt library was reset") }
        catch (_: org.json.JSONException) { }
        assertEquals("{broken", Files.readString(directory.resolve("library.json")))
    }

    @Test fun removingLibraryMembershipPreservesSavedContentAndProgress() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val chapter = ChapterKey(key, "/chapter")
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        library.download(chapter, pages)
        library.position(chapter, 0, 21)
        library.favorite(key, true)
        library.favorite(key, false)
        val restarted = LocalLibrary(directory)
        assertTrue(restarted.saved().isEmpty())
        assertEquals(pages, restarted.downloaded(chapter))
        assertEquals(ReadPosition("/chapter", 0, 21), restarted.book(key)!!.position)
    }

    @Test fun missingDownloadedFileReportsAnIntegrityFailureWithoutResettingTheRecord() {
        val directory = temporary.newFolder().toPath()
        val key = NovelKey(17, novel.url)
        val library = LocalLibrary(directory)
        library.remember(key, novel, chapters)
        library.download(ChapterKey(key, "/chapter"), pages)
        val file = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("chapter-") }.findFirst().get() }
        Files.delete(file)
        try { library.downloaded(ChapterKey(key, "/chapter")); fail("Missing download was silently accepted") }
        catch (_: java.nio.file.NoSuchFileException) { }
        assertTrue("/chapter" in library.book(key)!!.downloaded)
        assertFalse(Files.exists(file))
    }
}
