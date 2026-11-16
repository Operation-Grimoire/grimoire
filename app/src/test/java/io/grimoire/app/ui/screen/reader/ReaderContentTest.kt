package io.grimoire.app.ui.screen.reader

import io.grimoire.api.model.novel.*
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.ChapterStorageObservation
import io.grimoire.reading.storage.ChapterContentPresence
import org.junit.Assert.*
import org.junit.Test

class ReaderContentTest {
    private val key = ChapterKey(NovelKey(17, "/novel"), "/chapter")
    private val pages = listOf(NovelPage(99, PageContent.Text("")), NovelPage(99, PageContent.Text("   ")),
        NovelPage(99, PageContent.Text("Plain", "<b>Different</b>")), NovelPage(99, PageContent.Image("remote")),
        NovelPage(99, PageContent.Separator()), NovelPage(99, PageContent.Text("Last")))

    @Test fun savedProjectionMapsImagesAfterCanonicalCaptureAndKeepsLegacyAudioInput() {
        var lookups = 0
        val observation = ChapterStorageObservation(key, "scope", "generation", ChapterContentPresence.SAVED)
        val loaded = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED, "revision", observation)) {
            assertEquals(99, it.index); lookups++; "file:///cache"
        }
        assertEquals(1, lookups)
        assertSame(observation, loaded.storageObservation)
        assertEquals(observation.key, loaded.projection.document!!.key)
        assertEquals(pages, loaded.projection.originalPages)
        assertEquals(listOf(2, 3, 4, 5), loaded.projection.body.map { it.canonicalOrdinal })
        assertEquals(4, loaded.pages.size)
        assertEquals(PageContent.Image("file:///cache"), loaded.pages[1].content)
        val document = loaded.projection.document!!
        assertEquals(AnchorBlock.Image("remote"), document.blocks[3])
        assertEquals(CanonicalDocument.fromPages(key, pages).logicalRevision, document.logicalRevision)
        assertTrue(loaded.pages.all { it.index == 99 }) // Source indices remain available to existing audio/image consumers.
    }

    @Test fun sourceAndPreloadedAudioRetainUnfilteredPagesAndNeverUseSavedImageLookup() {
        for (origin in listOf(ChapterOrigin.SOURCE, ChapterOrigin.PRELOADED)) {
            val loaded = androidReaderContent(7, key, LoadedChapter(pages, origin)) { error("Saved-image lookup on $origin") }
            assertEquals(pages, loaded.pages)
            assertEquals(4, loaded.projection.body.size)
            assertEquals(PageContent.Image("remote"), loaded.projection.body[1].page.content)
        }
    }

    @Test fun captureKeepsRenderedLegacyFieldsSeparateFromCanonicalOrdinalAndReadHistory() {
        val loaded = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED))
        val position = captureReaderPosition(7, loaded, loaded.projection, 7, 0.4f, 1, 17)!!
        assertEquals(1, position.anchorIndex); assertEquals(17, position.anchorOffset)
        val block = position.contentAnchor!!.position as AnchorPosition.Block
        assertEquals(2, block.ordinal)
        assertEquals(AnchorPrecision.BLOCK_START, block.precision)
        val chapter = ChapterEntity(id = 7, novelId = 5, url = key.url, name = "Chapter", firstReadAt = 321, wordCount = 42)
        assertEquals(chapter.copy(readProgress = 0.4f, readAnchorItemIndex = 1, readAnchorItemOffset = 17), chapter.withReaderPosition(position))
        assertFalse(chapter.withReaderPosition(position).read)
    }

    @Test fun staleSameChapterReloadAndOtherChapterCallbacksAreRejected() {
        val old = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED))
        val fresh = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED))
        assertNotEquals(old.loadKey, fresh.loadKey)
        assertEquals(old.projection.document!!.logicalRevision, fresh.projection.document!!.logicalRevision)
        assertNull(captureReaderPosition(7, fresh, old.projection, 7, 0.4f, 1, 17))
        assertNull(captureReaderPosition(8, fresh, fresh.projection, 7, 0.4f, 1, 17))
        assertNull(captureReaderPosition(7, null, fresh.projection, 7, 0.4f, 1, 17))
        assertNull(captureReaderPosition(7, fresh, fresh.projection, 7, 0.4f, 99, 17))
        assertNull(captureReaderPosition(7, fresh, fresh.projection, 7, 0.4f, 1, -1))
        assertNotNull(captureReaderPosition(7, fresh, fresh.projection, 7, 0.4f, 1, 17))
    }

    @Test fun keylessLegacyCaptureAndHtmlDisagreementDoNotInventPortableTextOffsets() {
        val keyless = androidReaderContent(7, null, LoadedChapter(pages, ChapterOrigin.SAVED))
        val legacy = captureReaderPosition(7, keyless, keyless.projection, 7, 0.4f, 1, 17)!!
        assertNull(legacy.contentAnchor)
        assertEquals(1, legacy.anchorIndex)
        val known = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED))
        val coarse = known.projection.capture(1, RenderedTextPosition("Different", 3))!!
        val block = coarse.position as AnchorPosition.Block
        assertEquals(AnchorPrecision.BLOCK_START, block.precision)
        assertEquals(0, block.textOffset)
    }
}
