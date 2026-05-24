package io.grimoire.app.data.local.entity

import io.grimoire.api.model.NovelPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for [encodeChapterContent] / [decodeChapterContent]. The
 * encoder/decoder is what carries a downloaded chapter from disk back into
 * the reader, so any field that the source produces needs to survive a
 * round-trip — otherwise the offline copy is silently different from the
 * live fetch.
 */
class ChapterContentCodecTest {

    @Test
    fun plainTextPagesRoundTrip() {
        val pages = listOf(
            NovelPage(index = 0, text = "Hello world."),
            NovelPage(index = 1, text = "Second paragraph."),
        )
        val decoded = decodeChapterContent(encodeChapterContent(pages))
        assertEquals(2, decoded.size)
        assertEquals("Hello world.", decoded[0].text)
        assertEquals("Second paragraph.", decoded[1].text)
        assertNull(decoded[0].imageUrl)
        assertNull(decoded[0].formattedText)
        assertTrue(!decoded[0].isSeparator)
    }

    @Test
    fun imagePagesRoundTrip() {
        val pages = listOf(
            NovelPage(index = 0, text = "Before."),
            NovelPage(index = 1, text = "", imageUrl = "https://example.com/img.jpg"),
            NovelPage(index = 2, text = "After."),
        )
        val decoded = decodeChapterContent(encodeChapterContent(pages))
        assertEquals(3, decoded.size)
        assertEquals("https://example.com/img.jpg", decoded[1].imageUrl)
        assertEquals("", decoded[1].text)
    }

    @Test
    fun separatorPagesRoundTrip() {
        val pages = listOf(
            NovelPage(index = 0, text = "Scene one ends."),
            NovelPage(index = 1, text = "", isSeparator = true),
            NovelPage(index = 2, text = "Scene two begins."),
        )
        val decoded = decodeChapterContent(encodeChapterContent(pages))
        assertEquals(3, decoded.size)
        assertTrue("middle page should be a separator", decoded[1].isSeparator)
        assertEquals("", decoded[1].text)
        assertNull(decoded[1].imageUrl)
        assertNull(decoded[1].formattedText)
        assertEquals("Scene one ends.", decoded[0].text)
        assertEquals("Scene two begins.", decoded[2].text)
    }

    @Test
    fun formattedTextRoundTrips() {
        val pages = listOf(
            NovelPage(
                index = 0,
                text = "I need more information.",
                formattedText = "<i>I need more information.</i>",
            ),
        )
        val decoded = decodeChapterContent(encodeChapterContent(pages))
        assertEquals(1, decoded.size)
        assertEquals("I need more information.", decoded[0].text)
        assertEquals("<i>I need more information.</i>", decoded[0].formattedText)
    }

    @Test
    fun mixedPagesRoundTrip() {
        val pages = listOf(
            NovelPage(index = 0, text = "Opening."),
            NovelPage(index = 1, text = "Inner thought.", formattedText = "<i>Inner thought.</i>"),
            NovelPage(index = 2, text = "", isSeparator = true),
            NovelPage(index = 3, text = "", imageUrl = "https://example.com/illustration.jpg"),
            NovelPage(index = 4, text = "Closing."),
        )
        val decoded = decodeChapterContent(encodeChapterContent(pages))
        assertEquals(5, decoded.size)
        assertEquals("Opening.", decoded[0].text)
        assertEquals("<i>Inner thought.</i>", decoded[1].formattedText)
        assertTrue(decoded[2].isSeparator)
        assertEquals("https://example.com/illustration.jpg", decoded[3].imageUrl)
        assertEquals("Closing.", decoded[4].text)
    }

    @Test
    fun legacyEncodedContentWithoutNewMarkersStillDecodes() {
        // What a chapter persisted by an older app build would look like:
        // plain text pages joined by US, optionally with the RS image prefix.
        val legacy = "Page one." + CHAPTER_PAGE_SEPARATOR +
            CHAPTER_IMAGE_MARKER + "https://example.com/i.jpg" + CHAPTER_PAGE_SEPARATOR +
            "Page three."
        val decoded = decodeChapterContent(legacy)
        assertEquals(3, decoded.size)
        assertEquals("Page one.", decoded[0].text)
        assertEquals("https://example.com/i.jpg", decoded[1].imageUrl)
        assertEquals("Page three.", decoded[2].text)
        assertTrue(decoded.none { it.isSeparator })
        assertTrue(decoded.none { it.formattedText != null })
    }
}
