package io.grimoire.desktop

import io.grimoire.api.model.novel.*
import io.grimoire.reading.anchor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean

class ReaderProjectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pages = listOf(NovelPage(0, PageContent.Text("")), NovelPage(1, PageContent.Text("   ")),
        NovelPage(2, PageContent.Text("The harbor bell rang.")), NovelPage(3, PageContent.Image("remote")),
        NovelPage(4, PageContent.Separator()), NovelPage(5, PageContent.Text("Mira followed the light.")))

    @Test fun productionReaderCapturesCanonicalPositionButPersistsLegacyPixelsAndRetainsContent() = runBlocking {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory).apply { seed(); download(fixtureKey, pages) }
        val contentBefore = library.readSnapshot(fixtureKey)
        val model = desktopReaderModel(library, "1.5.0", AtomicBoolean(false))
        try {
            model.openSaved(library.book(fixtureKey.novel)!!); model.settled()
            model.read(fixtureChapters.first()); model.settled()
            val projection = model.state.value.projection!!
            assertEquals(6, projection.body.size)
            assertEquals(model.state.value.pages, projection.originalPages)
            model.position(fixtureKey, 2, 17, projection)
            val block = model.state.value.observedAnchor!!.position as AnchorPosition.Block
            assertEquals(2, block.ordinal); assertEquals(0, block.textOffset)
            assertEquals(AnchorPrecision.BLOCK_START, block.precision)
            model.flushPosition()
            assertEquals(ReadPosition("/chapter", 2, 17), library.book(fixtureKey.novel)!!.position)
            assertEquals(contentBefore, library.readSnapshot(fixtureKey))
        } finally { model.close() }
        LocalLibrary(directory).use { assertEquals(ReadPosition("/chapter", 2, 17), it.book(fixtureKey.novel)!!.position) }
    }

    @Test fun sameChapterReloadAndNavigationRejectOldProjectionCallbacks() = runBlocking {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        val model = desktopReaderModel(library, "1.5.0", AtomicBoolean(false))
        try {
            model.openSaved(library.book(fixtureKey.novel)!!); model.settled()
            model.read(fixtureChapters.first()); model.settled()
            val old = model.state.value.projection!!
            model.position(fixtureKey, 2, 17, old)
            model.read(fixtureChapters.first()); model.settled()
            val fresh = model.state.value.projection!!
            assertNotSame(old, fresh)
            assertEquals(old.document!!.logicalRevision, fresh.document!!.logicalRevision)
            model.position(fixtureKey, 5, 99, old)
            assertNull(model.state.value.observedAnchor)
            assertEquals(ReadPosition("/chapter", 2, 17), library.book(fixtureKey.novel)!!.position)
            model.position(fixtureKey, 3, 5, fresh)
            assertEquals(3, (model.state.value.observedAnchor!!.position as AnchorPosition.Block).ordinal)
            model.showLibrary(); model.settled()
            model.position(fixtureKey, 5, 99, fresh)
            assertNull(model.state.value.observedAnchor)
            assertEquals(ReadPosition("/chapter", 3, 5), library.book(fixtureKey.novel)!!.position)
        } finally { model.close() }
    }

    @Test fun invalidOrOtherKeySamplesCannotChangeTheCurrentLegacyPosition() = runBlocking {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        val model = desktopReaderModel(library, "1.5.0", AtomicBoolean(false))
        try {
            model.openSaved(library.book(fixtureKey.novel)!!); model.settled()
            model.read(fixtureChapters.first()); model.settled()
            val projection = model.state.value.projection!!
            for ((index, offset) in listOf(-1 to 0, 6 to 0, 0 to -1)) model.position(fixtureKey, index, offset, projection)
            model.position(fixtureKey.copy(url = "/other"), 2, 17, projection)
            assertNull(model.state.value.observedAnchor)
            assertNull(library.book(fixtureKey.novel)!!.position)
        } finally { model.close() }
    }

    private suspend fun ReaderModel.settled() { withTimeout(5_000) { state.first { !it.busy } }; assertNull(state.value.error) }
}
