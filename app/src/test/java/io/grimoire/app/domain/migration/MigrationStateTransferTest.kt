package io.grimoire.app.domain.migration

import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.*
import org.junit.Test

class MigrationStateTransferTest {
    private fun chapter(name: String, number: Float, read: Boolean = false, id: Long = 1) =
        ChapterEntity(id = id, novelId = 1, url = name, name = name, chapterNumber = number, read = read)

    @Test fun repeatedVolumeNumbersDoNotMarkTheNextVolumeRead() {
        val source = listOf(chapter("Volume 1 Chapter 1", 1f, true), chapter("Volume 2 Chapter 1", 1f))
        val target = source.mapIndexed { index, ch -> ch.copy(id = 10L + index, novelId = 2, read = false) }
        val actual = matchMigratedChapters(source, target)
        assertEquals(listOf("Volume 1 Chapter 1"), actual.map { it.name })
    }

    @Test fun ambiguousNumbersWithoutMatchingNamesAreSkipped() {
        val source = listOf(chapter("Volume 1 Chapter 1", 1f, true), chapter("Volume 2 Chapter 1", 1f))
        assertTrue(matchMigratedChapters(source, listOf(chapter("Different title", 1f))).isEmpty())
        assertTrue(matchMigratedChapters(listOf(source[0]),
            listOf(chapter("Renamed A", 1f), chapter("Renamed B", 1f))).isEmpty())
    }

    @Test fun normalizedNameWinsOverAConflictingNumber() {
        val source = listOf(chapter("The Beginning", 1f, true), chapter("Other", 2f))
        val target = listOf(chapter("  THE   BEGINNING ", 2f))
        assertTrue(matchMigratedChapters(source, target).single().read)
    }

    @Test fun namesAreReservedBeforeNumericFallbacks() {
        val source = listOf(chapter("Introduction", 1f, true))
        val target = listOf(chapter("New prologue", 1f), chapter("Introduction", 2f))
        assertEquals(listOf("Introduction"), matchMigratedChapters(source, target).map { it.name })
    }

    @Test fun uniqueParsedNumberSupportsRenamedChapters() {
        val source = listOf(chapter("Chapter 8: Before", -1f, true))
        val target = listOf(chapter("Chapter 8: After", -1f))
        assertTrue(matchMigratedChapters(source, target).single().read)
    }

    @Test fun unknownNumbersDoNotMatchUnrelatedNames() {
        assertTrue(matchMigratedChapters(listOf(chapter("Intro", -1f, true)),
            listOf(chapter("Opening", -1f))).isEmpty())
    }

    @Test fun partialProgressCarriesAnchorsWithoutChangingTargetIdentity() {
        val source = chapter("Chapter 1", 1f).copy(readProgress = 0.4f,
            readAnchorItemIndex = 8, readAnchorItemOffset = 25, firstReadAt = 100)
        val target = chapter("Chapter 1", 1f, id = 24).copy(novelId = 2, url = "new-url", downloadStatus = 3)
        val actual = matchMigratedChapters(listOf(source), listOf(target)).single()
        assertEquals(24L, actual.id)
        assertEquals(2L, actual.novelId)
        assertEquals("new-url", actual.url)
        assertEquals(3, actual.downloadStatus)
        assertEquals(0.4f, actual.readProgress, 0f)
        assertEquals(8, actual.readAnchorItemIndex)
        assertEquals(25, actual.readAnchorItemOffset)
        assertEquals(100L, actual.firstReadAt)
    }

    @Test fun metadataMovesUserChoicesAndKeepsTargetSourceMetadata() {
        val source = NovelEntity(id = 1, sourceId = 1, url = "old", title = "Old",
            favorite = true, categoryId = 7, chapterSortOrder = 2, lastReadAt = 100,
            notifyOnNewChapters = true, notifyOnNewLockedChapters = true, autoDownloadNewChapters = true,
            userRating = 9, readerTextAlign = 3, customCoverPath = "/covers/old", customCoverUrl = "custom-cover",
            overrideTitle = "My title", overrideAuthor = "My author", overrideDescription = "My synopsis",
            overrideStatus = 1, overrideGenres = "Custom")
        val target = NovelEntity(id = 2, sourceId = 2, url = "new", title = "Target", author = "Target author")
        val actual = target.withMigratedMetadata(source)
        assertEquals(2L, actual.id)
        assertEquals(2L, actual.sourceId)
        assertEquals("new", actual.url)
        assertEquals("Target", actual.title)
        assertEquals("Target author", actual.author)
        assertTrue(actual.favorite)
        assertEquals(source.categoryId, actual.categoryId)
        assertEquals(2, actual.chapterSortOrder)
        assertEquals(100L, actual.lastReadAt)
        assertTrue(actual.notifyOnNewChapters)
        assertTrue(actual.notifyOnNewLockedChapters)
        assertTrue(actual.autoDownloadNewChapters)
        assertEquals(9, actual.userRating)
        assertEquals(3, actual.readerTextAlign)
        assertEquals(source.customCoverPath, actual.customCoverPath)
        assertEquals(source.customCoverUrl, actual.customCoverUrl)
        assertEquals("My title", actual.effectiveTitle)
        assertEquals("My author", actual.effectiveAuthor)
        assertEquals(source.overrideDescription, actual.overrideDescription)
        assertEquals(source.overrideStatus, actual.overrideStatus)
        assertEquals(source.overrideGenres, actual.overrideGenres)
    }
}
