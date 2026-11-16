package io.grimoire.app.ui.screen.reader

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.*
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderProjectionTest {
    private val key = ChapterKey(NovelKey(17, "/novel"), "/chapter")
    private val pages = listOf(NovelPage(0, PageContent.Text("")), NovelPage(0, PageContent.Text("   ")),
        NovelPage(0, PageContent.Text("Prose")), NovelPage(0, PageContent.Image("remote")))

    @Test fun androidCanonicalMappingPreservesOriginalImageAndFrozenUtf16Framing() {
        val frame = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED)) { "file:///cache" }
        assertEquals(2, (frame.projection.capture(1)!!.position as AnchorPosition.Block).ordinal)
        assertEquals(AnchorBlock.Image("remote"), frame.projection.document!!.blocks[3])
        assertEquals(PageContent.Image("file:///cache"), frame.projection.body[1].page.content)
        val isolated = androidReaderContent(7, key, LoadedChapter(listOf(NovelPage(0, PageContent.Text("\uD800"))), ChapterOrigin.SAVED))
        // Frozen independent Python vector "isolated-high" from anchor contract v1.
        assertEquals("48fb9e71906161e98531bb5551807f77832335870cfb541bee55c243ba6e7b9f", isolated.projection.document!!.logicalRevision)
        assertNotNull(isolated.projection.capture(1, RenderedTextPosition("\uD800", 1)))
    }

    @Test fun provisionalCanonicalCaptureStillWritesOnlyTheRenderedLegacyPosition() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val novel = NovelEntity(id = 5, sourceId = 17, url = key.novel.url, title = "Novel")
            db.novelDao().upsert(novel)
            val before = ChapterEntity(id = 7, novelId = 5, url = key.url, name = "Chapter", firstReadAt = 321, wordCount = 42)
            db.chapterDao().insertAll(listOf(before))
            val novelBeforePosition = db.novelDao().getById(5)
            val version = db.openHelper.readableDatabase.version
            val frame = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED))
            val position = captureReaderPosition(7, frame, frame.projection, 7, 0.625f, 1, 17)!!
            assertEquals(2, (position.contentAnchor!!.position as AnchorPosition.Block).ordinal)
            db.chapterDao().setReadAnchor(position.chapterId, position.fraction, position.anchorIndex, position.anchorOffset)
            assertEquals(before.copy(readProgress = 0.625f, readAnchorItemIndex = 1, readAnchorItemOffset = 17), db.chapterDao().getByUrl(5, key.url))
            assertEquals(novelBeforePosition, db.novelDao().getById(5))
            assertEquals(version, db.openHelper.readableDatabase.version)
        } finally { db.close() }
    }

    @Test fun reloadRejectsOldProjectionAndSourceAudioDoesNotLoseBlankPages() {
        val old = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SOURCE)) { error("No source cache lookup") }
        val current = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SOURCE))
        assertEquals(pages, current.pages)
        assertEquals(2, current.projection.body.size)
        assertNull(captureReaderPosition(7, current, old.projection, 7, 0.5f, 1, 0))
        assertNotNull(captureReaderPosition(7, current, current.projection, 7, 0.5f, 1, 0))
    }

    @Test fun textPrecisionAndHiddenContentRemainExplicitOnArt() {
        val frame = androidReaderContent(7, key, LoadedChapter(pages, ChapterOrigin.SAVED))
        val document = frame.projection.document!!
        val hidden = ReadingAnchor(key, document.logicalRevision,
            AnchorPosition.Block(0, document.blockFingerprint(0), 0, AnchorPrecision.BLOCK_START))
        val target = frame.projection.resolve(hidden)!!.target as ProjectionTarget.Item
        assertTrue(target.presentationFallback); assertEquals(1, target.renderedIndex)
        val coarse = frame.projection.capture(1, RenderedTextPosition("different", 2))!!.position as AnchorPosition.Block
        assertEquals(AnchorPrecision.BLOCK_START, coarse.precision)
        val exact = frame.projection.capture(1, RenderedTextPosition("Prose", 2))!!.position as AnchorPosition.Block
        assertEquals(AnchorPrecision.TEXT_OFFSET, exact.precision)
        assertEquals(2, exact.textOffset)
    }
}
