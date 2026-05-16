package io.grimoire.app.novelupdates

import io.grimoire.app.data.novelupdates.NovelUpdatesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelUpdatesMatcherTest {

    @Test
    fun normalize_stripsNoiseAndParentheticals() {
        assertEquals(
            "that time i got reincarnated as slime",
            NovelUpdatesMatcher.normalize("That Time I Got Reincarnated as a Slime (WN)"),
        )
        assertEquals(
            "solo leveling",
            NovelUpdatesMatcher.normalize("  Solo  Leveling!! "),
        )
    }

    @Test
    fun similarity_identicalIsOne_andEmptyIsZero() {
        assertEquals(1.0, NovelUpdatesMatcher.similarity("omniscient reader", "omniscient reader"), 0.0)
        assertEquals(0.0, NovelUpdatesMatcher.similarity("", "anything"), 0.0)
    }

    @Test
    fun similarity_closeTitlesScoreHigh_distinctScoreLow() {
        val close = NovelUpdatesMatcher.similarity(
            NovelUpdatesMatcher.normalize("Lord of the Mysteries"),
            NovelUpdatesMatcher.normalize("Lord of Mysteries"),
        )
        val far = NovelUpdatesMatcher.similarity(
            NovelUpdatesMatcher.normalize("Lord of the Mysteries"),
            NovelUpdatesMatcher.normalize("Release That Witch"),
        )
        assertTrue("expected close match high, was $close", close >= 0.85)
        assertTrue("expected unrelated low, was $far", far < 0.45)
    }
}
