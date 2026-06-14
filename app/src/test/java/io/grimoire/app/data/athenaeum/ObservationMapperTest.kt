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
    fun chapter_decimalNumber_roundsToTwoPlaces() {
        // 12.34f widened to Double leaks float noise; the mapper must round so the
        // backend (max 2 decimals) accepts it. The serialized value must be 12.34.
        val ch = ChapterEntity(
            id = 9L, novelId = 1L, url = "/c", name = "x", chapterNumber = 12.34f,
        )
        val item = ObservationMapper.chapter("https://royalroad.com", "en", novel, ch)!!
        assertEquals(12.34, item.number!!, 0.0)
    }

    @Test
    fun chapter_derivesNumberFromUrlWhenSourceHasNone() {
        // novelfull-style: extension leaves chapterNumber unset; number lives in the URL slug.
        val ch = ChapterEntity(
            id = 9L, novelId = 1L,
            url = "/shadow-slave/chapter-37-getting-to-know-each-other.html",
            name = "Getting to Know Each Other", chapterNumber = -1f,
        )
        val item = ObservationMapper.chapter("https://novelfull.com", "en", novel, ch)!!
        assertEquals(37.0, item.number!!, 0.0)
    }

    @Test
    fun chapter_derivesNumberFromName() {
        val ch = ChapterEntity(id = 9L, novelId = 1L, url = "/x", name = "Chapter 12: The Fall", chapterNumber = -1f)
        val item = ObservationMapper.chapter("https://novelfull.com", "en", novel, ch)!!
        assertEquals(12.0, item.number!!, 0.0)
    }

    @Test
    fun chapter_withoutAnyNumber_isSkipped() {
        val ch = ChapterEntity(id = 9L, novelId = 1L, url = "/x", name = "Prologue", chapterNumber = -1f)
        assertNull(ObservationMapper.chapter("https://royalroad.com", "en", novel, ch))
    }

    private fun ch(id: Long, name: String, number: Float = -1f, url: String = "/x") =
        ChapterEntity(id = id, novelId = 1L, url = url, name = name, chapterNumber = number)

    @Test
    fun chapters_infersMissingNumbersFromNeighbours() {
        // Reading order: 101 (explicit), an unnumbered interlude, 102 (explicit).
        val ordered = listOf(
            ch(1, "Chapter 101", number = 101f),
            ch(2, "Interlude: The Calm"),
            ch(3, "Chapter 102", number = 102f),
        )
        val items = ObservationMapper.chapters("https://novelfull.com", "en", novel, ordered)
        assertEquals(3, items.size)
        assertEquals(101.0, items[0].number!!, 0.0)
        assertEquals(101.5, items[1].number!!, 0.0) // interpolated between neighbours
        assertEquals(102.0, items[2].number!!, 0.0)
    }

    @Test
    fun chapters_noNumbersAnywhere_fallsBackToPosition() {
        val ordered = listOf(ch(1, "Prologue"), ch(2, "The Beginning"), ch(3, "Onwards"))
        val items = ObservationMapper.chapters("https://novelfull.com", "en", novel, ordered)
        assertEquals(listOf(1.0, 2.0, 3.0), items.map { it.number })
    }
}
