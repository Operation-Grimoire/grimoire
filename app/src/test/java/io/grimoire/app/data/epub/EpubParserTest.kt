package io.grimoire.app.data.epub

import io.grimoire.app.data.book.BookPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `minimal EPUB produces a neutral parsed book`() {
        val file = temporary.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.entry("META-INF/container.xml") {
                """
                <container><rootfiles><rootfile full-path="book.opf" /></rootfiles></container>
                """.trimIndent()
            }
            zip.entry("book.opf") {
                """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata><dc:title>Neutral book</dc:title><dc:language>en</dc:language></metadata>
                  <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" /></manifest>
                  <spine><itemref idref="chapter" /></spine>
                </package>
                """.trimIndent()
            }
            zip.entry("chapter.xhtml") {
                """
                <html><head><title>Chapter one</title></head><body><p>Chapter text.</p></body></html>
                """.trimIndent()
            }
        }

        val parsed = EpubParser.parse(file, "Fallback.epub")

        assertEquals("Neutral book", parsed.title)
        assertEquals("en", parsed.language)
        assertEquals(listOf("Chapter one"), parsed.chapters.map { it.title })
        assertEquals(listOf("Chapter text."), parsed.chapters.single().pages.map { (it as BookPage.Text).text })
    }

    private fun ZipOutputStream.entry(path: String, content: () -> String) {
        putNextEntry(ZipEntry(path))
        write(content().toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
