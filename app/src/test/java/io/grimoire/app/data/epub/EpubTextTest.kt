package io.grimoire.app.data.epub

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class EpubTextTest {
    @Test fun validUnicodeAndEscapedMarkupRoundTripThroughAnXmlParser() {
        val text = "& <> \"'\t\n中文😀"
        val document = "<p>${escapeEpubXml(text)}</p>".toByteArray(Charsets.UTF_8)
        val parsed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(document.inputStream())
        assertEquals(text, parsed.documentElement.textContent)
    }

    @Test fun reservedControlsAreRejectedRatherThanDroppedOrWrittenAsInvalidXml() {
        for (code in 28..31) {
            for (text in listOf("${code.toChar()}before", "before${code.toChar()}after", "after${code.toChar()}")) {
                try { escapeEpubXml(text); fail("Accepted XML-incompatible control $code") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }
}
