package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import org.junit.Assert.*
import org.junit.Test

class ReadingCoreTest {
    @Test fun compatibilityRetainsAndroidLegacyAndRejectsMalformedRequirements() {
        assertTrue(supportsExtensionApi(null, "1.5.0"))
        assertTrue(supportsExtensionApi("1.5", "1.5.1-SNAPSHOT"))
        for (required in listOf("2.0", "1.6", "1", "01.5", "1.5.0", "1.2147483648", " 1.5")) {
            assertFalse(required, supportsExtensionApi(required, "1.5.0"))
        }
        for (host in listOf("1.5", "garbage", "1.5.0-beta", "2147483648.0.0")) {
            assertFalse(host, supportsExtensionApi("1.5", host))
        }
    }

    @Test fun existingPersistedTokensDecodeWithoutChangingAndroidBytes() {
        val stored = "Arabic prose\u001c<i>Arabic prose</i>\u001f\u001d\u001f\u001ehttps://example.org/image.png"
        val pages = listOf(
            NovelPage(0, PageContent.Text("Arabic prose", "<i>Arabic prose</i>")),
            NovelPage(1, PageContent.Separator()),
            NovelPage(2, PageContent.Image("https://example.org/image.png")),
        )
        assertEquals(stored, encodeChapterContent(pages))
        val decoded = decodeChapterContent(stored)
        assertEquals(pages[0], decoded[0])
        assertTrue(decoded[1].content is PageContent.Separator)
        assertEquals(pages[2], decoded[2])
        assertEquals(listOf(NovelPage(0, PageContent.Text("Legacy prose"))), decodeChapterContent("Legacy prose"))
    }

    @Test fun chapterKeysIncludeSourceNovelAndChapterWithoutLocalRowIds() {
        val key = ChapterKey(NovelKey(12, "/book"), "/chapter")
        assertNotEquals(key, key.copy(novel = NovelKey(13, "/book")))
        assertNotEquals(key, key.copy(novel = NovelKey(12, "/other")))
        assertEquals(key, ChapterKey(NovelKey(12, "/book"), "/chapter"))
    }
}
