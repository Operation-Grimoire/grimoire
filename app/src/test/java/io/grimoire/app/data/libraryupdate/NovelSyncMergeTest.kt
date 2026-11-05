package io.grimoire.app.data.libraryupdate

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelSyncMergeTest {
    private val old = NovelEntity(
        id = 5, sourceId = 1, url = "book", title = "Title", favorite = true,
        lastUpdated = 100, categoryId = 3, lastReadAt = 70,
        overrideTitle = "My title", customCoverUrl = "my-cover", userRating = 8,
    )
    private val fetched = Novel(url = "book", title = "Title", language = Language.UNKNOWN)

    @Test
    fun `unchanged refresh preserves timestamp and needs no write`() {
        assertSame(old, mergeNovelMetadata(old, fetched, 200))
    }

    @Test
    fun `first successful details fetch initializes the cache timestamp`() {
        assertEquals(200L, mergeNovelMetadata(old.copy(lastUpdated = 0), fetched, 200).lastUpdated)
    }

    @Test
    fun `source change preserves current local edits`() {
        val current = old.copy(
            favorite = false, categoryId = 9, lastReadAt = 150,
            overrideTitle = "Edited while fetching", overrideStatus = NovelStatus.HIATUS.ordinal,
            notifyOnNewChapters = true, autoDownloadNewChapters = true,
        )
        val actual = mergeNovelMetadata(current, fetched.copy(title = "New source title"), 200)
        assertEquals(current.copy(title = "New source title", lastUpdated = 200), actual)
    }

    @Test
    fun `incomplete source response keeps known metadata`() {
        val current = old.copy(
            thumbnailUrl = "cover", author = "Author", description = "Synopsis",
            genres = "Fantasy", status = NovelStatus.ONGOING.ordinal,
        )
        assertSame(current, mergeNovelMetadata(current, fetched.copy(title = " "), 200))
    }

    @Test
    fun `only completed effective status is excluded from bulk sync`() {
        for (status in NovelStatus.entries) {
            assertEquals(status != NovelStatus.COMPLETED, shouldSyncNovel(old.copy(status = status.ordinal)))
        }
        assertTrue(shouldSyncNovel(old.copy(status = Int.MAX_VALUE)))
        assertFalse(shouldSyncNovel(old.copy(overrideStatus = NovelStatus.COMPLETED.ordinal)))
        assertTrue(shouldSyncNovel(old.copy(
            status = NovelStatus.COMPLETED.ordinal, overrideStatus = NovelStatus.ONGOING.ordinal,
        )))
    }
}
