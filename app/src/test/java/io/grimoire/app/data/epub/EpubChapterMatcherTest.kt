package io.grimoire.app.data.epub

import io.grimoire.app.data.local.entity.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EpubChapterMatcherTest {

    private fun prior(
        index: Int,
        title: String,
        read: Boolean = false,
    ) = ChapterEntity(
        id = (index + 1).toLong(),
        novelId = 1L,
        url = "epub:test/$index",
        name = title,
        chapterNumber = (index + 1).toFloat(),
        read = read,
    )

    @Test
    fun unchangedReimport_matchesEveryChapter() {
        val previous = listOf(prior(0, "One", read = true), prior(1, "Two"))
        val result = matchPreviousEpubChapters(previous, listOf("One", "Two"))
        assertSame(previous[0], result[0])
        assertSame(previous[1], result[1])
    }

    @Test
    fun insertedPrologue_shiftsNothing_titlesCarryState() {
        val previous = listOf(
            prior(0, "One", read = true),
            prior(1, "Two", read = true),
            prior(2, "Three"),
        )
        val result = matchPreviousEpubChapters(
            previous,
            listOf("Prologue", "One", "Two", "Three"),
        )
        assertNull(result[0]) // the new prologue starts unread
        assertSame(previous[0], result[1])
        assertSame(previous[1], result[2])
        assertSame(previous[2], result[3])
    }

    @Test
    fun removedLeadingChapter_titlesStillCarryState() {
        val previous = listOf(
            prior(0, "Foreword"),
            prior(1, "One", read = true),
            prior(2, "Two"),
        )
        val result = matchPreviousEpubChapters(previous, listOf("One", "Two"))
        assertSame(previous[1], result[0])
        assertSame(previous[2], result[1])
    }

    @Test
    fun duplicateTitles_sameCount_fallsBackToPosition() {
        val previous = listOf(
            prior(0, "Chapter", read = true),
            prior(1, "Chapter"),
        )
        val result = matchPreviousEpubChapters(previous, listOf("Chapter", "Chapter"))
        assertSame(previous[0], result[0])
        assertSame(previous[1], result[1])
    }

    @Test
    fun duplicateTitles_countChanged_inheritsNothing() {
        val previous = listOf(
            prior(0, "Chapter", read = true),
            prior(1, "Chapter", read = true),
        )
        val result = matchPreviousEpubChapters(
            previous,
            listOf("Chapter", "Chapter", "Chapter"),
        )
        // A shifted edition with ambiguous titles must not guess: better to
        // reset than to mark the wrong chapter read.
        assertNull(result[0])
        assertNull(result[1])
        assertNull(result[2])
    }

    @Test
    fun mixedTitles_uniqueOnesMatch_ambiguousFollowPositionWhenCountUnchanged() {
        val previous = listOf(
            prior(0, "Intro", read = true),
            prior(1, "Chapter"),
            prior(2, "Chapter", read = true),
        )
        val result = matchPreviousEpubChapters(
            previous,
            listOf("Intro", "Chapter", "Chapter"),
        )
        assertSame(previous[0], result[0])
        assertSame(previous[1], result[1])
        assertSame(previous[2], result[2])
    }

    @Test
    fun firstImport_noPrevious_allFresh() {
        val result = matchPreviousEpubChapters(emptyList(), listOf("One", "Two"))
        assertEquals(listOf(null, null), result)
    }

    @Test
    fun unsortedPreviousRows_orderedByChapterNumberBeforePositionalFallback() {
        // DAO order is not guaranteed; positional fallback must follow the
        // original spine order (chapterNumber), not row order.
        val previous = listOf(
            prior(1, "Chapter"),
            prior(0, "Chapter", read = true),
        )
        val result = matchPreviousEpubChapters(previous, listOf("Chapter", "Chapter"))
        assertSame(previous[1], result[0]) // chapterNumber 1 (read) is position 0
        assertSame(previous[0], result[1])
    }
}
