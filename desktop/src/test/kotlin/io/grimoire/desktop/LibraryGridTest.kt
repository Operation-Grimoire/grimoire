package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.reading.NovelKey
import io.grimoire.ui.library.ChapterCounts
import org.junit.Assert.*
import org.junit.Test

class LibraryGridTest {
    private val key = NovelKey(5, "/book")
    private val chapters = listOf(Chapter("/1", "One"), Chapter("/2", "Two", locked = true), Chapter("/3", "Three"))

    @Test fun bookRecordKeepsCoverGenresAndStatus() {
        val novel = Novel(key.url, "Mother of Learning", Language.EN, "https://covers.example/mol.jpg", "nobody103",
            "A time loop.", listOf("Fantasy", "Magic"), NovelStatus.COMPLETED)
        val decoded = decodeBook(encodeBook(SavedBook(key, novel, chapters, true, setOf("/1"), null, null)))
        assertEquals("https://covers.example/mol.jpg", decoded.novel.thumbnailUrl)
        assertEquals(listOf("Fantasy", "Magic"), decoded.novel.genres)
        assertEquals(NovelStatus.COMPLETED, decoded.novel.status)
    }

    @Test fun recordsWrittenBeforeCoversStillRead() {
        val old = encodeBook(SavedBook(key, Novel(key.url, "Old", Language.EN), chapters, true, emptySet(), null, null))
        old.remove("thumbnailUrl"); old.remove("genres"); old.remove("status")
        val decoded = decodeBook(old)
        assertNull(decoded.novel.thumbnailUrl)
        assertTrue(decoded.novel.genres.isEmpty())
        assertEquals(NovelStatus.UNKNOWN, decoded.novel.status)
    }

    @Test fun searchMatchesTitleOrAuthorAndCountsIgnoreStaleDownloads() {
        val first = SavedBook(key, Novel(key.url, "Mother of Learning", Language.EN, author = "nobody103"), chapters, true, setOf("/1", "/gone"), null, null)
        val second = SavedBook(NovelKey(5, "/other"), Novel("/other", "Super Supportive", Language.EN, author = "Sleyca"), chapters, true, emptySet(), null, null)
        assertEquals(listOf(first, second), filterLibrary(listOf(first, second), "  "))
        assertEquals(listOf(first), filterLibrary(listOf(first, second), "MOTHER"))
        assertEquals(listOf(second), filterLibrary(listOf(first, second), "sleyca"))
        assertEquals(ChapterCounts(total = 3, readCount = 0, downloadedCount = 1, lockedCount = 1), first.counts())
    }
}
