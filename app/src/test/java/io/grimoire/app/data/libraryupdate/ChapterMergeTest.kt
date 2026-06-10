package io.grimoire.app.data.libraryupdate

import io.grimoire.api.model.Chapter
import io.grimoire.app.data.local.entity.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ChapterMergeTest {

    private fun entity(
        id: Long,
        url: String,
        name: String = "Chapter $id",
        read: Boolean = false,
        readProgress: Float = 0f,
    ) = ChapterEntity(
        id = id,
        novelId = 1L,
        url = url,
        name = name,
        read = read,
        readProgress = readProgress,
    )

    private fun chapter(url: String, name: String, locked: Boolean = false) =
        Chapter(url = url, name = name, locked = locked)

    @Test
    fun appendAtEnd_keepsStateAndLeavesNewUnmatched() {
        val existing = listOf(
            entity(1, "u/1", "One", read = true),
            entity(2, "u/2", "Two"),
        )
        val fetched = listOf(
            chapter("u/1", "One"),
            chapter("u/2", "Two"),
            chapter("u/3", "Three"),
        )
        val merge = matchChapters(existing, fetched)
        assertSame(existing[0], merge.priors[0])
        assertSame(existing[1], merge.priors[1])
        assertNull(merge.priors[2])
        assertEquals(2, merge.matchedByUrl)
        assertEquals(0, merge.matchedByName)
        assertEquals(0, merge.droppedRead)
    }

    @Test
    fun insertAtBeginning_stableUrls_newChapterStartsFresh() {
        val existing = listOf(
            entity(1, "u/ch-one", "One", read = true),
            entity(2, "u/ch-two", "Two", read = true),
        )
        val fetched = listOf(
            chapter("u/prologue", "Prologue"),
            chapter("u/ch-one", "One"),
            chapter("u/ch-two", "Two"),
        )
        val merge = matchChapters(existing, fetched)
        assertNull(merge.priors[0])
        assertSame(existing[0], merge.priors[1])
        assertSame(existing[1], merge.priors[2])
        assertEquals(0, merge.droppedRead)
    }

    @Test
    fun urlDrift_uniqueNames_rescuedByNamePass() {
        val existing = listOf(
            entity(1, "u/old-1", "One", read = true, readProgress = 1f),
            entity(2, "u/old-2", "Two", read = true),
            entity(3, "u/old-3", "Three"),
        )
        val fetched = listOf(
            chapter("u/new-1", "One"),
            chapter("u/new-2", "Two"),
            chapter("u/new-3", "Three"),
        )
        val merge = matchChapters(existing, fetched)
        assertSame(existing[0], merge.priors[0])
        assertSame(existing[1], merge.priors[1])
        assertSame(existing[2], merge.priors[2])
        assertEquals(0, merge.matchedByUrl)
        assertEquals(3, merge.matchedByName)
        assertEquals(0, merge.droppedRead)
    }

    @Test
    fun urlDriftWithInsertedPrologue_existingKeepState_prologueFresh() {
        val existing = listOf(
            entity(1, "u/old-1", "One", read = true),
            entity(2, "u/old-2", "Two"),
        )
        val fetched = listOf(
            chapter("u/new-0", "Prologue"),
            chapter("u/new-1", "One"),
            chapter("u/new-2", "Two"),
        )
        val merge = matchChapters(existing, fetched)
        assertNull(merge.priors[0])
        assertSame(existing[0], merge.priors[1])
        assertSame(existing[1], merge.priors[2])
        assertEquals(2, merge.matchedByName)
    }

    @Test
    fun duplicateNames_neverMatchedByName() {
        val existing = listOf(
            entity(1, "u/old-1", "Chapter", read = true),
            entity(2, "u/old-2", "Chapter", read = true),
        )
        val fetched = listOf(
            chapter("u/new-1", "Chapter"),
            chapter("u/new-2", "Chapter"),
        )
        val merge = matchChapters(existing, fetched)
        assertNull(merge.priors[0])
        assertNull(merge.priors[1])
        assertEquals(0, merge.matchedByName)
        assertEquals(2, merge.droppedRead)
    }

    @Test
    fun blankNames_neverMatchedByName() {
        val existing = listOf(entity(1, "u/old-1", "", read = true))
        val fetched = listOf(chapter("u/new-1", ""))
        val merge = matchChapters(existing, fetched)
        assertNull(merge.priors[0])
        assertEquals(1, merge.droppedRead)
    }

    @Test
    fun urlMatchWins_namePassOnlySeesLeftovers() {
        // "Two" was renamed to "One" upstream while a genuinely new "One" URL
        // appeared: the URL match must claim the existing row first so the
        // name pass cannot reassign it.
        val existing = listOf(entity(1, "u/1", "One", read = true))
        val fetched = listOf(
            chapter("u/1", "Renamed"),
            chapter("u/9", "One"),
        )
        val merge = matchChapters(existing, fetched)
        assertSame(existing[0], merge.priors[0])
        assertNull(merge.priors[1])
        assertEquals(1, merge.matchedByUrl)
        assertEquals(0, merge.matchedByName)
    }

    @Test
    fun whitespaceInNames_trimmedForMatching() {
        val existing = listOf(entity(1, "u/old", " One ", read = true))
        val fetched = listOf(chapter("u/new", "One"))
        val merge = matchChapters(existing, fetched)
        assertSame(existing[0], merge.priors[0])
        assertEquals(1, merge.matchedByName)
    }

    @Test
    fun emptyExisting_allFetchedFresh() {
        val merge = matchChapters(emptyList(), listOf(chapter("u/1", "One")))
        assertNull(merge.priors[0])
        assertEquals(0, merge.matchedByUrl)
        assertEquals(0, merge.droppedRead)
    }
}
