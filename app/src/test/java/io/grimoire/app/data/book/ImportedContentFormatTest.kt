package io.grimoire.app.data.book

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.txt.TxtParser
import io.grimoire.reading.decodeChapterContent
import io.grimoire.reading.encodeChapterContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedContentFormatTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun txtControlsReachStorageAsTextRatherThanChapterStructure() {
        val text = "before\u001cplain\u001dtext\u001ewith\u001fcontrols 中文😀"
        val file = temporary.newFile("controls.txt").apply { writeText(text, Charsets.UTF_8) }
        val parsed = TxtParser.parse(file, "controls.txt")
        val imported = (parsed.chapters.single().pages.single() as BookPage.Text).text
        val stored = encodeChapterContent(listOf(NovelPage(0, PageContent.Text(imported))))
        assertEquals(listOf(NovelPage(0, PageContent.Text(text))), decodeChapterContent(stored))
    }
}
