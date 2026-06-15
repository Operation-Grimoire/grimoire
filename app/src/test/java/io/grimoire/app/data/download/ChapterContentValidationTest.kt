package io.grimoire.app.data.download

import io.grimoire.api.model.NovelPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ChapterContentValidationTest {

    @Test
    fun hasReadableContent_emptyList_isFalse() {
        assertFalse(hasReadableContent(emptyList()))
    }

    @Test
    fun hasReadableContent_blankAndSeparatorOnly_isFalse() {
        val pages = listOf(
            NovelPage(index = 0, text = "   "),
            NovelPage(index = 1, text = "", isSeparator = true),
            NovelPage(index = 2, text = "\n\t"),
        )
        assertFalse(hasReadableContent(pages))
    }

    @Test
    fun hasReadableContent_anyProse_isTrue() {
        val pages = listOf(
            NovelPage(index = 0, text = "  "),
            NovelPage(index = 1, text = "Real prose here."),
        )
        assertTrue(hasReadableContent(pages))
    }

    @Test
    fun hasReadableContent_imageOnly_isTrue() {
        val pages = listOf(NovelPage(index = 0, text = "", imageUrl = "https://x/i.png"))
        assertTrue(hasReadableContent(pages))
    }

    @Test
    fun hasReadableContent_formattedOnly_isTrue() {
        val pages = listOf(NovelPage(index = 0, text = "", formattedText = "<b>hi</b>"))
        assertTrue(hasReadableContent(pages))
    }

    @Test
    fun fetchReadablePages_returnsFirstNonEmptyResult() = runBlocking {
        val attempts = AtomicInteger(0)
        val pages = fetchReadablePages(sleep = {}) {
            attempts.incrementAndGet()
            listOf(NovelPage(index = 0, text = "content"))
        }
        assertEquals(1, attempts.get())
        assertEquals("content", pages.single().text)
    }

    @Test
    fun fetchReadablePages_retriesEmptyThenSucceeds() = runBlocking {
        val attempts = AtomicInteger(0)
        val slept = mutableListOf<Long>()
        val pages = fetchReadablePages(sleep = { slept += it }) {
            if (attempts.incrementAndGet() < 3) emptyList()
            else listOf(NovelPage(index = 0, text = "finally"))
        }
        assertEquals(3, attempts.get())
        // Backed off between the two empty attempts.
        assertEquals(listOf(1_000L, 2_000L), slept)
        assertEquals("finally", pages.single().text)
    }

    @Test(expected = EmptyChapterContentException::class)
    fun fetchReadablePages_alwaysEmpty_throws() = runBlocking {
        fetchReadablePages(sleep = {}) { emptyList<NovelPage>() }
        Unit
    }

    @Test
    fun fetchReadablePages_propagatesFetchException() = runBlocking {
        val attempts = AtomicInteger(0)
        try {
            fetchReadablePages(sleep = {}) {
                attempts.incrementAndGet()
                throw IllegalStateException("boom")
            }
            throw AssertionError("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        // Hard failures are not retried — surfaced on the first attempt.
        assertEquals(1, attempts.get())
    }
}
