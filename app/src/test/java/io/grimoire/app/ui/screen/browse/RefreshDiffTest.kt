package io.grimoire.app.ui.screen.browse

import io.grimoire.app.data.local.entity.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshDiffTest {

    private fun chapter(
        id: Long,
        locked: Boolean = false,
    ) = ChapterEntity(
        id = id,
        novelId = 1L,
        url = "https://example.test/$id",
        name = "Chapter $id",
        chapterNumber = id.toFloat(),
        locked = locked,
    )

    private fun List<ChapterEntity>.byUrl() = associateBy { it.url }

    @Test
    fun `diff reports chapters absent before the refresh`() {
        val before = listOf(chapter(1), chapter(2)).byUrl()
        val after = listOf(chapter(1), chapter(2), chapter(3))
        assertEquals(listOf(3L), diffNewChapters(before, after).map { it.id })
    }

    @Test
    fun `diff reports a locked chapter that became unlocked`() {
        val before = listOf(chapter(1), chapter(2, locked = true)).byUrl()
        val after = listOf(chapter(1), chapter(2, locked = false))
        assertEquals(listOf(2L), diffNewChapters(before, after).map { it.id })
    }

    @Test
    fun `diff ignores unchanged and still-locked chapters`() {
        val before = listOf(chapter(1), chapter(2, locked = true)).byUrl()
        val after = listOf(chapter(1), chapter(2, locked = true))
        assertTrue(diffNewChapters(before, after).isEmpty())
    }

    @Test
    fun `notify filter keeps readable chapters only when readable notifications are on`() {
        val new = listOf(chapter(1), chapter(2, locked = true))
        val out = filterNotifiableChapters(
            new,
            notifyOnNewChapters = true,
            notifyOnNewLockedChapters = false,
        )
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun `notify filter keeps locked chapters only when locked notifications are on`() {
        val new = listOf(chapter(1), chapter(2, locked = true))
        val out = filterNotifiableChapters(
            new,
            notifyOnNewChapters = false,
            notifyOnNewLockedChapters = true,
        )
        assertEquals(listOf(2L), out.map { it.id })
    }

    @Test
    fun `notify filter is empty when both toggles are off`() {
        val new = listOf(chapter(1), chapter(2, locked = true))
        val out = filterNotifiableChapters(
            new,
            notifyOnNewChapters = false,
            notifyOnNewLockedChapters = false,
        )
        assertTrue(out.isEmpty())
    }
}
