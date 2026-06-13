package io.grimoire.app.data.athenaeum

import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationMapperTest {

    private val novel = NovelEntity(
        id = 1L,
        sourceId = 7L,
        url = "/fiction/12345/some-novel",
        title = "Some Novel",
        thumbnailUrl = "https://cdn.example.com/c.jpg",
        description = "A novel that is some.",
        status = 1, // ONGOING
        language = "en",
    )

    @Test
    fun series_mapsFieldsAndResolvesRelativeUrl() {
        val item = ObservationMapper.series("https://royalroad.com", "en", novel)
        assertEquals("SERIES", item.kind)
        assertEquals("royalroad.com", item.platformDomain)
        assertEquals("https://royalroad.com/fiction/12345/some-novel", item.url)
        assertEquals("Some Novel", item.title)
        assertEquals("ONGOING", item.status)
        assertEquals("en", item.language)
        assertEquals("WEB", item.format)
        assertNull("releaseKind is left to the platform default", item.releaseKind)
    }

    @Test
    fun series_stripsWwwFromPlatformDomain() {
        val item = ObservationMapper.series("https://www.royalroad.com", "en", novel)
        assertEquals("royalroad.com", item.platformDomain)
    }

    @Test
    fun chapter_mapsAndKeepsAbsoluteUrl() {
        val ch = ChapterEntity(
            id = 9L, novelId = 1L,
            url = "https://royalroad.com/fiction/12345/chapter/1",
            name = "Chapter One", uploadDate = 1_700_000_000_000L, chapterNumber = 1f,
        )
        val item = ObservationMapper.chapter("https://royalroad.com", "en", novel, ch)!!
        assertEquals("CHAPTER", item.kind)
        assertEquals("https://royalroad.com/fiction/12345/chapter/1", item.url)
        assertEquals("https://royalroad.com/fiction/12345/some-novel", item.seriesUrl)
        assertEquals(1.0, item.number!!, 0.0001)
        assertTrue(item.publishedAt!!.startsWith("2023-"))
    }

    @Test
    fun chapter_withoutNumber_isSkipped() {
        val ch = ChapterEntity(id = 9L, novelId = 1L, url = "/x", name = "x", chapterNumber = -1f)
        assertNull(ObservationMapper.chapter("https://royalroad.com", "en", novel, ch))
    }
}
