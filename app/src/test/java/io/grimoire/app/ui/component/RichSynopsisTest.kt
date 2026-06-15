package io.grimoire.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichSynopsisTest {

    @Test
    fun `plain text without links is one plain segment`() {
        val segments = parsePlainSynopsis("Just a normal synopsis.", detectLinks = true)
        assertEquals(listOf(SynopsisSegment.Plain("Just a normal synopsis.")), segments)
    }

    @Test
    fun `newlines are preserved in plain segments`() {
        val text = "Line one\nLine two"
        val segments = parsePlainSynopsis(text, detectLinks = true)
        assertEquals(listOf(SynopsisSegment.Plain(text)), segments)
    }

    @Test
    fun `http url is detected as a link`() {
        val segments = parsePlainSynopsis("See https://example.com for more", detectLinks = true)
        assertEquals(
            listOf(
                SynopsisSegment.Plain("See "),
                SynopsisSegment.Link("https://example.com", "https://example.com"),
                SynopsisSegment.Plain(" for more"),
            ),
            segments,
        )
    }

    @Test
    fun `www url gets an https scheme prepended`() {
        val segments = parsePlainSynopsis("visit www.example.com", detectLinks = true)
        assertEquals(
            listOf(
                SynopsisSegment.Plain("visit "),
                SynopsisSegment.Link("www.example.com", "https://www.example.com"),
            ),
            segments,
        )
    }

    @Test
    fun `trailing punctuation stays outside the link`() {
        val segments = parsePlainSynopsis("Read (https://example.com/page).", detectLinks = true)
        assertEquals(
            listOf(
                SynopsisSegment.Plain("Read ("),
                SynopsisSegment.Link("https://example.com/page", "https://example.com/page"),
                SynopsisSegment.Plain(")."),
            ),
            segments,
        )
    }

    @Test
    fun `multiple links in one string`() {
        val segments = parsePlainSynopsis("a https://x.com b https://y.com", detectLinks = true)
        assertEquals(
            listOf(
                SynopsisSegment.Plain("a "),
                SynopsisSegment.Link("https://x.com", "https://x.com"),
                SynopsisSegment.Plain(" b "),
                SynopsisSegment.Link("https://y.com", "https://y.com"),
            ),
            segments,
        )
    }

    @Test
    fun `links are not detected when detection disabled`() {
        val text = "See https://example.com here"
        val segments = parsePlainSynopsis(text, detectLinks = false)
        assertEquals(listOf(SynopsisSegment.Plain(text)), segments)
    }

    @Test
    fun `html markup is detected`() {
        assertTrue(containsHtmlMarkup("First line<br>Second line"))
        assertTrue(containsHtmlMarkup("""A <a href="https://x.com">link</a>"""))
        assertTrue(containsHtmlMarkup("<p>Wrapped</p>"))
        assertTrue(containsHtmlMarkup("Bold <strong>text</strong>"))
        assertTrue(containsHtmlMarkup("ten &amp; more"))
    }

    @Test
    fun `plain prose with stray angle bracket is not html`() {
        assertFalse(containsHtmlMarkup("He said x < y and walked off"))
        assertFalse(containsHtmlMarkup("A perfectly normal synopsis with a url https://x.com"))
    }
}
