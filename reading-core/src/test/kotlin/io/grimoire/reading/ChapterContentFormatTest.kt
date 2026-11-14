package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import org.junit.Assert.*
import org.junit.Test

class ChapterContentFormatTest {
    @Test fun wireFormatIsExplicitAndLegacyJsonLookingTextIsNeverSniffed() {
        val stored = encodeChapterContent(listOf(NovelPage(0, PageContent.Text("A")),
            NovelPage(1, PageContent.Image("https://example.org/i")), NovelPage(2, PageContent.Separator())))
        assertEquals(2, stored.version)
        assertEquals("""[["text","A",null],["image","https://example.org/i"],["separator"]]""", stored.content)
        assertEquals(listOf(NovelPage(0, PageContent.Text(stored.content))),
            decodeChapterContent(StoredChapterContent(stored.content, 1)))
    }

    @Test fun unknownVersionsAndMalformedPagesNeverFallBackToLegacyProse() {
        for (version in listOf(-1, 0, 3, Int.MAX_VALUE)) {
            try { decodeChapterContent(StoredChapterContent("legacy", version)); fail("Accepted version $version") }
            catch (_: IllegalArgumentException) { }
        }
        for (content in listOf("legacy", "{}", "[null]", "[[]]", "[[\"unknown\"]]",
            "[[\"text\",null,null]]", "[[\"text\",\"a\"]]", "[[\"text\",\"a\",2]]",
            "[[\"image\",7]]", "[[\"separator\",\"extra\"]]")) {
            try { decodeChapterContent(StoredChapterContent(content, 2)); fail("Accepted malformed page $content") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun storageUtf8RoundTripPreservesUnicodeAndNullCharacters() {
        val pages = listOf(NovelPage(0, PageContent.Text("中文 😀 \u0000\u001c\u001d\u001e\u001f")))
        val stored = encodeChapterContent(pages)
        assertFalse(stored.content.any { it.code < 32 })
        assertPages(pages, decodeChapterContent(stored.copy(content = stored.content.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))))
    }

    @Test fun wordCountUsesProseOnlyAndIgnoresWireHtmlAndImages() {
        val pages = listOf(NovelPage(0, PageContent.Text("one two", "<b>one two</b>")),
            NovelPage(1, PageContent.Image("https://example.org/image words")),
            NovelPage(2, PageContent.Separator()), NovelPage(3, PageContent.Text("三 四")))
        assertEquals(4, chapterWordCount(decodeChapterContent(encodeChapterContent(pages))))
    }

    @Test fun reservedMarkersAreLiteralTextIncludingAtTheStart() {
        for (marker in 28..31) {
            val text = marker.toChar() + "before" + marker.toChar() + "after" + marker.toChar()
            val pages = listOf(NovelPage(0, PageContent.Text(marker.toChar().toString())),
                NovelPage(1, PageContent.Text(text)))
            assertPages(pages, decodeChapterContent(encodeChapterContent(pages)))
        }
    }

    @Test fun reservedMarkersInHtmlAndImageUrlsSurviveMixedPages() {
        val controls = (28..31).map(Int::toChar).joinToString("")
        val pages = listOf(
            NovelPage(0, PageContent.Text("Prose $controls", "<i>$controls HTML</i>")),
            NovelPage(1, PageContent.Image("https://example.org/$controls.jpg")),
            NovelPage(2, PageContent.Separator()),
            NovelPage(3, PageContent.Text("After")),
        )
        assertPages(pages, decodeChapterContent(encodeChapterContent(pages)))
    }

    @Test fun emptyChapterIsDistinctFromOneEmptyTextPage() {
        assertEquals(emptyList<NovelPage>(), decodeChapterContent(encodeChapterContent(emptyList())))
        val pages = listOf(NovelPage(0, PageContent.Text("")), NovelPage(1, PageContent.Text("", "")))
        assertPages(pages, decodeChapterContent(encodeChapterContent(pages)))
    }

    @Test fun unicodeQuotesAndAllAsciiControlsSurvive() {
        val text = "مرحبا 世界 😀 \\\"\n" + (0..31).map(Int::toChar).joinToString("")
        val pages = listOf(NovelPage(0, PageContent.Text(text, "<p>$text</p>")))
        assertPages(pages, decodeChapterContent(encodeChapterContent(pages)))
    }

    private fun assertPages(expected: List<NovelPage>, actual: List<NovelPage>) {
        assertEquals("page count", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (before, after) ->
            assertEquals(index, after.index)
            when (val content = before.content) {
                is PageContent.Separator -> assertTrue(after.content is PageContent.Separator)
                else -> assertEquals(content, after.content)
            }
        }
    }
}
