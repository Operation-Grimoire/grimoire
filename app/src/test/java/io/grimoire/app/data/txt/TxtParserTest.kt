package io.grimoire.app.data.txt

import io.grimoire.app.data.book.BookPage
import io.grimoire.app.data.book.ParsedBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset

class TxtParserTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `plain text without headings becomes one chapter`() {
        val parsed = parse("First paragraph.\n\nSecond paragraph.\n")

        assertEquals("My novel", parsed.title)
        assertEquals(1, parsed.chapters.size)
        assertEquals("My novel", parsed.chapters.single().title)
        assertEquals(listOf("First paragraph.", "Second paragraph."), textPages(parsed))
        assertNull(parsed.author)
        assertNull(parsed.cover)
    }

    @Test fun `english chapter headings split chapters and omit heading lines from content`() {
        val parsed = parse("Chapter 1: Start\nFirst.\nChapter 2 - Next\nSecond.\n")

        assertEquals(listOf("Chapter 1: Start", "Chapter 2 - Next"), parsed.chapters.map { it.title })
        assertEquals(listOf("First."), textPages(parsed, 0))
        assertEquals(listOf("Second."), textPages(parsed, 1))
    }

    @Test fun `chinese chapter headings split chapters`() {
        val parsed = parse("第一章 开始\n正文一\n第二回：继续\n正文二\n")

        assertEquals(listOf("第一章 开始", "第二回：继续"), parsed.chapters.map { it.title })
        assertEquals(listOf("正文一"), textPages(parsed, 0))
        assertEquals(listOf("正文二"), textPages(parsed, 1))
    }

    @Test fun `text before the first heading is retained`() {
        val parsed = parse("Preamble.\n\nChapter 1\nText.\n")

        assertEquals(listOf("My novel", "Chapter 1"), parsed.chapters.map { it.title })
        assertEquals(listOf("Preamble."), textPages(parsed, 0))
        assertEquals(listOf("Text."), textPages(parsed, 1))
    }

    @Test fun `hard-wrapped lines retain indentation and become paragraphs`() {
        val parsed = parse("  First line\nsecond line\n\nNext paragraph\n")

        assertEquals(1, parsed.chapters.size)
        assertEquals(listOf("  First line\nsecond line", "Next paragraph"), textPages(parsed))
    }

    @Test fun `blank lines and nonnumeric chapter prose do not create chapters`() {
        val parsed = parse("Chapter one is not a heading.\n\nStill the same book.\n")

        assertEquals(1, parsed.chapters.size)
        assertEquals(
            listOf("Chapter one is not a heading.", "Still the same book."),
            textPages(parsed),
        )
    }

    @Test fun `UTF-8 BOM is removed`() {
        val parsed = parseBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Chapter 1\nUnicode ✓".toByteArray(Charsets.UTF_8))

        assertEquals("Chapter 1", parsed.chapters.single().title)
        assertEquals(listOf("Unicode ✓"), textPages(parsed))
    }

    @Test fun `UTF-16 BOMs are honored`() {
        for ((charset, bom) in listOf(
            Charsets.UTF_16LE to byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            Charsets.UTF_16BE to byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
        )) {
            val parsed = parseBytes(bom + "Chapter 1\nUnicode ✓".toByteArray(charset))
            assertEquals(listOf("Unicode ✓"), textPages(parsed))
        }
    }

    @Test fun `GB18030 text is decoded`() {
        val parsed = parseBytes("第一章 开始\n这是正文。".toCharset("GB18030"))

        assertEquals("第一章 开始", parsed.chapters.single().title)
        assertEquals(listOf("这是正文。"), textPages(parsed))
    }

    @Test fun `Shift_JIS text is decoded`() {
        val parsed = parseBytes("第一章\nこれは本文です。".toCharset("Shift_JIS"))

        assertEquals("第一章", parsed.chapters.single().title)
        assertEquals(listOf("これは本文です。"), textPages(parsed))
    }

    @Test fun `Windows-1252 text is decoded`() {
        val parsed = parseBytes("“Smart quotes”".toCharset("windows-1252"))

        assertEquals(listOf("“Smart quotes”"), textPages(parsed))
    }

    @Test fun `Windows-1252 accented prose is not misclassified as GB18030`() {
        val text = "Une sélection spéciale de caractères."
        val parsed = parseBytes(text.toCharset("windows-1252"))

        assertEquals(listOf(text), textPages(parsed))
    }

    @Test fun `legacy detection continues past an ASCII-only prefix`() {
        val text = "Une sélection spéciale de caractères."
        val parsed = parseBytes(("a".repeat(70_000) + "\n\n" + text).toCharset("windows-1252"))

        assertEquals(text, textPages(parsed).last())
    }

    @Test fun `UTF-32 BOMs are explicitly rejected`() {
        for ((charset, bom) in listOf(
            Charset.forName("UTF-32LE") to byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0),
            Charset.forName("UTF-32BE") to byteArrayOf(0, 0, 0xFE.toByte(), 0xFF.toByte()),
        )) {
            val bytes = bom + "Text".toByteArray(charset)
            assertThrows(IllegalArgumentException::class.java) { parseBytes(bytes) }
        }
    }

    @Test fun `empty text is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { parse(" \n\n") }
    }

    private fun String.toCharset(name: String): ByteArray = toByteArray(Charset.forName(name))

    private fun parse(text: String, charset: Charset = Charsets.UTF_8): ParsedBook =
        parseBytes(text.toByteArray(charset))

    private fun parseBytes(bytes: ByteArray): ParsedBook {
        val file = temporary.newFile().apply { writeBytes(bytes) }
        return TxtParser.parse(file, "My novel.txt")
    }

    private fun textPages(parsed: ParsedBook, chapter: Int = 0): List<String> =
        parsed.chapters[chapter].pages.map { (it as BookPage.Text).text }
}
