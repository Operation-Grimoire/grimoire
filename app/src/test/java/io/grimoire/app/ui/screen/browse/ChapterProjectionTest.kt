package io.grimoire.app.ui.screen.browse

import io.grimoire.app.data.local.entity.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterProjectionTest {

    private fun chapter(
        id: Long,
        name: String = "Chapter $id",
        number: Float = id.toFloat(),
        uploadDate: Long = id * 1000L,
    ) = ChapterEntity(
        id = id,
        novelId = 1L,
        url = "https://example.test/$id",
        name = name,
        chapterNumber = number,
        uploadDate = uploadDate,
    )

    @Test
    fun `NUMBER_ASC returns input order untouched`() {
        val input = listOf(chapter(1), chapter(2), chapter(3))
        val out = projectChapters(input, ChapterSort.NUMBER_ASC, "")
        assertEquals(listOf(1L, 2L, 3L), out.map { it.id })
    }

    @Test
    fun `NUMBER_DESC reverses input order`() {
        val input = listOf(chapter(1), chapter(2), chapter(3))
        val out = projectChapters(input, ChapterSort.NUMBER_DESC, "")
        assertEquals(listOf(3L, 2L, 1L), out.map { it.id })
    }

    @Test
    fun `DATE_ASC sorts by uploadDate ascending`() {
        val input = listOf(
            chapter(1, uploadDate = 300L),
            chapter(2, uploadDate = 100L),
            chapter(3, uploadDate = 200L),
        )
        val out = projectChapters(input, ChapterSort.DATE_ASC, "")
        assertEquals(listOf(2L, 3L, 1L), out.map { it.id })
    }

    @Test
    fun `DATE_DESC sorts by uploadDate descending`() {
        val input = listOf(
            chapter(1, uploadDate = 300L),
            chapter(2, uploadDate = 100L),
            chapter(3, uploadDate = 200L),
        )
        val out = projectChapters(input, ChapterSort.DATE_DESC, "")
        assertEquals(listOf(1L, 3L, 2L), out.map { it.id })
    }

    @Test
    fun `empty query returns full sorted list`() {
        val input = listOf(chapter(1, "First"), chapter(2, "Second"))
        val out = projectChapters(input, ChapterSort.NUMBER_ASC, "")
        assertEquals(2, out.size)
    }

    @Test
    fun `whitespace-only query is treated as empty`() {
        val input = listOf(chapter(1, "First"), chapter(2, "Second"))
        val out = projectChapters(input, ChapterSort.NUMBER_ASC, "   ")
        assertEquals(2, out.size)
    }

    @Test
    fun `query filters chapter names case-insensitively`() {
        val input = listOf(
            chapter(1, "Prologue"),
            chapter(2, "Chapter One: The Beginning"),
            chapter(3, "The End"),
        )
        val out = projectChapters(input, ChapterSort.NUMBER_ASC, "the")
        assertEquals(listOf(2L, 3L), out.map { it.id })
    }

    @Test
    fun `query is applied after sort`() {
        val input = listOf(
            chapter(1, "Alpha", uploadDate = 300L),
            chapter(2, "Beta", uploadDate = 100L),
            chapter(3, "Alpha 2", uploadDate = 200L),
        )
        val out = projectChapters(input, ChapterSort.DATE_ASC, "alpha")
        // DATE_ASC sort: [Beta(100), Alpha 2(200), Alpha(300)]
        // Filter "alpha" keeps Alpha 2 then Alpha (case-insensitive contains)
        assertEquals(listOf(3L, 1L), out.map { it.id })
    }

    @Test
    fun `query with no matches returns empty list`() {
        val input = listOf(chapter(1, "Foo"), chapter(2, "Bar"))
        val out = projectChapters(input, ChapterSort.NUMBER_ASC, "xyz")
        assertTrue(out.isEmpty())
    }

    @Test
    fun `empty chapter list returns empty`() {
        val out = projectChapters(emptyList(), ChapterSort.NUMBER_ASC, "anything")
        assertTrue(out.isEmpty())
    }
}
