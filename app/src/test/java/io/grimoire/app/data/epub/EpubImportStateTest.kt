package io.grimoire.app.data.epub

import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.*
import org.junit.Test

class EpubImportStateTest {
    private val parsed = ParsedEpub("New title", "New author", "New synopsis", listOf("Fantasy"),
        "en", null, emptyList())

    @Test fun reimportRefreshesSourceMetadataAndKeepsUserChoices() {
        val existing = NovelEntity(id = 42, sourceId = 7, url = "book", title = "Old title",
            favorite = true, categoryId = 3, chapterSortOrder = 2, lastReadAt = 123,
            userRating = 9, readerTextAlign = 3, autoDownloadNewChapters = true,
            notifyOnNewChapters = true, notifyOnNewLockedChapters = true,
            customCoverPath = "/covers/custom", customCoverUrl = "https://cover",
            overrideTitle = "My title", overrideAuthor = "My author",
            overrideDescription = "My synopsis", overrideStatus = 1, overrideGenres = "Custom")
        val actual = mergeEpubMetadata(existing, parsed, 7, "book", "new-cover", false, false, 500)
        assertEquals(42L, actual.id)
        assertEquals("New title", actual.title)
        assertEquals("New author", actual.author)
        assertEquals("new-cover", actual.thumbnailUrl)
        assertEquals("Fantasy", actual.genres)
        assertEquals(500L, actual.lastUpdated)
        assertTrue(actual.favorite)
        assertEquals(existing.categoryId, actual.categoryId)
        assertEquals(existing.chapterSortOrder, actual.chapterSortOrder)
        assertEquals(123L, actual.lastReadAt)
        assertEquals(9, actual.userRating)
        assertEquals(3, actual.readerTextAlign)
        assertTrue(actual.autoDownloadNewChapters)
        assertTrue(actual.notifyOnNewChapters)
        assertTrue(actual.notifyOnNewLockedChapters)
        assertEquals(existing.customCoverPath, actual.customCoverPath)
        assertEquals(existing.customCoverUrl, actual.customCoverUrl)
        assertEquals("My title", actual.effectiveTitle)
        assertEquals("My author", actual.effectiveAuthor)
        assertEquals(existing.overrideDescription, actual.overrideDescription)
        assertEquals(existing.overrideStatus, actual.overrideStatus)
        assertEquals(existing.overrideGenres, actual.overrideGenres)
    }

    @Test fun newLocalImportUsesImportDefaults() {
        val actual = mergeEpubMetadata(null, parsed, 7, "book", null, true, true, 500)
        assertTrue(actual.favorite)
        assertEquals(500L, actual.lastReadAt)
        assertEquals(NovelStatus.COMPLETED.ordinal, actual.status)
        assertEquals("English", actual.language)
    }

    @Test fun absentCoverAndUnknownLanguageKeepEarlierMetadata() {
        val existing = NovelEntity(sourceId = 7, url = "book", title = "Old",
            thumbnailUrl = "old-cover", language = "French")
        val actual = mergeEpubMetadata(existing, parsed.copy(language = null), 7, "book", null, false, true, 500)
        assertEquals("old-cover", actual.thumbnailUrl)
        assertEquals("French", actual.language)
        assertEquals(500L, actual.lastReadAt)
    }

    @Test fun shiftedChapterKeepsItsReadingAnchorAndNewDownloadIdentity() {
        val previous = ChapterEntity(id = 9, novelId = 42, url = "book/0", name = "Chapter 1",
            chapterNumber = 1f, readProgress = 0.4f, readAnchorItemIndex = 8,
            readAnchorItemOffset = 25, firstReadAt = 123)
        val prior = matchPreviousEpubChapters(listOf(previous), listOf("Prologue", "Chapter 1"))
        val current = ChapterEntity(novelId = 42, url = "book/1", name = "Chapter 1",
            chapterNumber = 2f, downloadStatus = 3, wordCount = 700).withEpubReadingState(prior[1])
        assertEquals(8, current.readAnchorItemIndex)
        assertEquals(25, current.readAnchorItemOffset)
        assertEquals(0.4f, current.readProgress, 0f)
        assertEquals(123L, current.firstReadAt)
        assertEquals("book/1", current.url)
        assertEquals(0L, current.id)
        assertEquals(700, current.wordCount)
        assertEquals(3, current.downloadStatus)
        assertNull(prior[0])
    }
}
