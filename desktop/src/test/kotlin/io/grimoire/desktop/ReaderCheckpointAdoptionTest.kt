package io.grimoire.desktop

import io.grimoire.api.model.novel.*
import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class ReaderCheckpointAdoptionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pages = listOf("First", "Second", "Third").mapIndexed { i, text -> NovelPage(i, PageContent.Text(text)) }

    @Test fun chapterRefreshAndReopenFallBackFromMissingOrLockedResumeSelection() {
        val directory = temporary.newFolder().toPath()
        val legacy = fixtureChapters.first()
        val selected = legacy.copy(url = "/selected", locked = false)
        LocalLibrary(directory).use { library ->
            library.seed()
            library.remember(fixtureKey.novel, fixtureNovel, listOf(legacy, selected))
            library.position(fixtureKey, 1, 9); library.flush()
            library.selectResumeChapter(ChapterKey(fixtureKey.novel, selected.url))
            assertEquals(selected, library.book(fixtureKey.novel)!!.resumeChapter())
            library.remember(fixtureKey.novel, fixtureNovel, listOf(legacy, selected.copy(locked = true)))
            assertEquals(legacy, library.book(fixtureKey.novel)!!.resumeChapter())
            library.remember(fixtureKey.novel, fixtureNovel, listOf(legacy))
        }
        LocalLibrary(directory).use { reopened ->
            val book = reopened.book(fixtureKey.novel)!!
            assertEquals(selected.url, book.resumeChapterUrl)
            assertEquals(legacy, book.resumeChapter())
            assertNull(book.copy(chapters = listOf(legacy.copy(locked = true))).resumeChapter())
        }
    }
    private fun model(library: LocalLibrary) = desktopReaderModel(library, "1.6.0", AtomicBoolean(false))
    private suspend fun ReaderModel.settled() { withTimeout(5_000) { state.first { !it.busy } }; assertNull(state.value.error) }
    private suspend fun ReaderModel.open(library: LocalLibrary, chapter: Chapter = fixtureChapters.first()) {
        openSaved(library.book(fixtureKey.novel)!!); settled(); read(chapter); settled()
    }

    @Test fun productionCloseAndReopenRestorePortableBlockWithoutRewritingItFromLayout() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory).apply { seed(); download(fixtureKey, pages) }
        val before = library.readSnapshot(fixtureKey)
        var reader = model(library)
        reader.open(library)
        val projection = reader.state.value.projection!!
        reader.position(fixtureKey, 2, 17, projection)
        reader.close()
        LocalLibrary(directory).use { reopened ->
            val stored = DesktopReadingCheckpointDao(reopened).read(fixtureKey)!!
            assertEquals(2, (stored.position as AnchorPosition.Block).ordinal)
            reader = model(reopened)
            try {
                reader.open(reopened)
                assertEquals(ReaderViewport(2), reader.state.value.checkpointLoad!!.restore.viewport)
                assertTrue(reader.state.value.checkpointLoad!!.restore.logical is AnchorResolution.Exact)
                reader.position(fixtureKey, 2, 0, reader.state.value.projection!!, userInitiated = false)
                reader.flushPosition()
                assertEquals(stored, DesktopReadingCheckpointDao(reopened).read(fixtureKey))
                assertEquals(ReadPosition(fixtureKey.url, 2, 17), reopened.book(fixtureKey.novel)!!.position)
                assertEquals(before, reopened.readSnapshot(fixtureKey))
            } finally { reader.close() }
        }
    }

    @Test fun navigationStoresEachChapterAndRejectsRetiredProjectionAndSameChapterCallbacks() = runBlocking<Unit> {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        val second = Chapter("/second", "Second")
        library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters + second)
        val secondKey = fixtureKey.copy(url = second.url); library.download(secondKey, pages)
        val reader = model(library)
        try {
            reader.open(library); val firstProjection = reader.state.value.projection!!
            reader.position(fixtureKey, 2, 17, firstProjection)
            reader.read(second); reader.settled()
            assertEquals(secondKey.url, library.book(fixtureKey.novel)!!.resumeChapterUrl)
            assertEquals(fixtureKey.url, library.book(fixtureKey.novel)!!.position!!.chapterUrl)
            val secondProjection = reader.state.value.projection!!
            reader.position(fixtureKey, 0, 0, firstProjection)
            reader.position(secondKey, 1, 9, secondProjection); reader.flushPosition()
            assertEquals(secondKey.url, library.book(fixtureKey.novel)!!.position!!.chapterUrl)
            assertEquals(2, (DesktopReadingCheckpointDao(library).read(fixtureKey)!!.position as AnchorPosition.Block).ordinal)
            reader.read(fixtureChapters.first()); reader.settled()
            assertEquals(2, reader.state.value.checkpointLoad!!.restore.viewport.index)
            reader.position(fixtureKey, 0, 0, firstProjection); reader.flushPosition()
            assertEquals(2, (DesktopReadingCheckpointDao(library).read(fixtureKey)!!.position as AnchorPosition.Block).ordinal)
            assertEquals(1, (DesktopReadingCheckpointDao(library).read(secondKey)!!.position as AnchorPosition.Block).ordinal)
        } finally { reader.close() }
    }

    @Test fun changedChapterFallbackRetainsOldAnchorAndLegacyPositionUntilUserMoves() = runBlocking<Unit> {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        val reader = model(library)
        try {
            reader.open(library); reader.position(fixtureKey, 2, 17, reader.state.value.projection!!); reader.flushPosition()
            val old = DesktopReadingCheckpointDao(library).read(fixtureKey)
            val legacy = library.book(fixtureKey.novel)!!.position
            library.download(fixtureKey, prose("Replacement")); reader.read(fixtureChapters.first()); reader.settled()
            val projection = reader.state.value.projection!!
            assertEquals(CheckpointNotice.CHANGED, reader.state.value.checkpointLoad!!.restore.notice)
            reader.position(fixtureKey, 0, 0, projection, userInitiated = false); reader.flushPosition()
            assertEquals(old, DesktopReadingCheckpointDao(library).read(fixtureKey)); assertEquals(legacy, library.book(fixtureKey.novel)!!.position)
            reader.position(fixtureKey, 0, 0, projection); reader.flushPosition()
            assertEquals(projection.document!!.logicalRevision, DesktopReadingCheckpointDao(library).read(fixtureKey)!!.logicalRevision)
            assertNull(reader.state.value.positionNotice)
        } finally { reader.close() }
    }

    @Test fun failedAutosaveRetainsLaterCaptureAndCanBeRetriedWithoutLosingIt() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory).apply { seed(); download(fixtureKey, pages) }
        val reader = model(library); val marker = directory.resolve("library.json"); val original = Files.readAllBytes(marker)
        try {
            reader.open(library); val projection = reader.state.value.projection!!
            reader.position(fixtureKey, 1, 9, projection)
            Files.delete(marker); Files.createDirectory(marker)
            reader.flushPosition(); assertTrue(reader.state.value.error!!.contains("Could not save reading position"))
            reader.position(fixtureKey, 2, 17, projection)
            Files.delete(marker); Files.write(marker, original)
            reader.flushPosition(); assertNull(reader.state.value.error)
            assertEquals(2, (DesktopReadingCheckpointDao(library).read(fixtureKey)!!.position as AnchorPosition.Block).ordinal)
        } finally {
            if (Files.isDirectory(marker)) { Files.delete(marker); Files.write(marker, original) }
            reader.close()
        }
    }

    @Test fun failedCloseResumesSameWriterAndLaterCaptureSurvivesSuccessfulClose() = runBlocking<Unit> {
        var fail = false
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory) { if (fail && it == StorageCheckpoint.BEFORE_COMMIT) throw IOException("Checkpoint close fault") }
        library.seed(); library.download(fixtureKey, pages)
        val reader = model(library); reader.open(library)
        val projection = reader.state.value.projection!!; reader.position(fixtureKey, 1, 9, projection)
        fail = true
        try { reader.close(); fail("Close failure must be surfaced") } catch (e: IOException) { assertEquals("Checkpoint close fault", e.message) }
        fail = false
        reader.position(fixtureKey, 2, 17, projection); reader.close()
        LocalLibrary(directory).use { reopened ->
            assertEquals(2, (DesktopReadingCheckpointDao(reopened).read(fixtureKey)!!.position as AnchorPosition.Block).ordinal)
        }
    }

    @Test fun revokedCheckpointCannotPublishItsBufferedLegacyResumePosition() = runBlocking<Unit> {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply {
            seed(); download(fixtureKey, pages); position(fixtureKey, 0, 0); flush()
        }
        val reader = model(library)
        try {
            reader.open(library); val old = reader.state.value.projection!!
            reader.position(fixtureKey, 2, 17, old)
            library.download(fixtureKey, prose("Changed"))
            reader.flushPosition()
            assertEquals(ReadPosition(fixtureKey.url, 0, 0), library.book(fixtureKey.novel)!!.position)
            assertEquals(CheckpointNotice.RELOAD.message, reader.state.value.positionNotice)
            assertNull(DesktopReadingCheckpointDao(library).read(fixtureKey))
            reader.position(fixtureKey, 1, 9, old); reader.flushPosition()
            assertEquals(ReadPosition(fixtureKey.url, 0, 0), library.book(fixtureKey.novel)!!.position)
        } finally { reader.close() }
    }

    @Test fun oldFlushCannotDiscardANewerViewsPendingResumeSelectionForTheSameChapter() = runBlocking<Unit> {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); val retired = Any(); val fresh = Any()
            library.position(fixtureKey, 1, 9, retired)
            library.position(fixtureKey, 2, 17, fresh)
            library.discardPendingPosition(fixtureKey, retired); library.flush()
            assertEquals(ReadPosition(fixtureKey.url, 2, 17), library.book(fixtureKey.novel)!!.position)
        }
    }

    @Test fun navigationClaimRetiresCallbacksBeforeItsCoroutineCanStart() = runBlocking<Unit> {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        val reader = ReaderModel(library, "1.6.0", AtomicBoolean(false), ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(library))),
            LibraryRepository(DesktopLibraryDao(library)), DownloadRepository(DesktopDownloadDao(library)), operationDispatcher = dispatcher)
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        try {
            reader.open(library); val projection = reader.state.value.projection!!
            reader.position(fixtureKey, 1, 9, projection)
            val blocker = CoroutineScope(dispatcher).launch {
                entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
            check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            reader.showLibrary()
            reader.position(fixtureKey, 2, 17, projection)
            assertEquals(ReadPosition(fixtureKey.url, 1, 9), library.book(fixtureKey.novel)!!.position)
            release.countDown(); blocker.join(); reader.settled()
            assertEquals(1, (DesktopReadingCheckpointDao(library).read(fixtureKey)!!.position as AnchorPosition.Block).ordinal)
        } finally { release.countDown(); reader.close(); dispatcher.close() }
    }

    @Test fun semanticRejectionDropsOnlyItsLegacyBufferAndFreshReloadCanRecover() = runBlocking<Unit> {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages); position(fixtureKey, 0, 0); flush() }
        var reject = true
        val delegate = DesktopReadingCheckpointDao(library)
        val dao = object : ReadingCheckpointDao by delegate {
            override suspend fun commit(command: CheckpointCommand): CheckpointCommit =
                if (reject) CheckpointCommit.NotApplied(CheckpointRejection.INVALID) else delegate.commit(command)
        }
        val reader = ReaderModel(library, "1.6.0", AtomicBoolean(false), ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(library))),
            LibraryRepository(DesktopLibraryDao(library)), DownloadRepository(DesktopDownloadDao(library)), dao)
        try {
            reader.open(library); reader.position(fixtureKey, 2, 17, reader.state.value.projection!!); reader.flushPosition()
            assertNotNull(reader.state.value.error); assertEquals(ReadPosition(fixtureKey.url, 0, 0), library.book(fixtureKey.novel)!!.position)
            reject = false; reader.read(fixtureChapters.first()); reader.settled()
            reader.position(fixtureKey, 1, 9, reader.state.value.projection!!); reader.flushPosition()
            assertEquals(1, (delegate.read(fixtureKey)!!.position as AnchorPosition.Block).ordinal)
        } finally { reader.close() }
    }

    @Test fun savedChapterStillJoinsLibraryWhenItsFreshCheckpointRegistrationFails() = runBlocking<Unit> {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        var failRegistration = false
        val delegate = DesktopReadingCheckpointDao(library)
        val dao = object : ReadingCheckpointDao by delegate {
            override suspend fun register(observation: ChapterStorageObservation, document: CanonicalDocument): ReadingSession? {
                if (failRegistration) throw IOException("Reload fault")
                return delegate.register(observation, document)
            }
        }
        val reader = ReaderModel(library, "1.6.0", AtomicBoolean(false), ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(library))),
            LibraryRepository(DesktopLibraryDao(library)), DownloadRepository(DesktopDownloadDao(library)), dao)
        try {
            reader.open(library); failRegistration = true; reader.download()
            withTimeout(5_000) { reader.state.first { !it.busy } }
            assertTrue(reader.state.value.error!!.contains("could not reload"))
            assertTrue(library.book(fixtureKey.novel)!!.favorite); assertEquals(fixtureKey.novel, library.saved().single().key)
            assertEquals(CheckpointNotice.RELOAD.message, reader.state.value.positionNotice)
        } finally { reader.close() }
    }

    @Test fun membershipFailureStillRefreshesSavedSessionForContinuedReading() = runBlocking<Unit> {
        val library = LocalLibrary(temporary.newFolder().toPath()).apply { seed(); download(fixtureKey, pages) }
        val delegate = DesktopLibraryDao(library)
        val membership = object : LibraryDao by delegate {
            override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? { throw IOException("Membership fault") }
        }
        val reader = ReaderModel(library, "1.6.0", AtomicBoolean(false), ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(library))),
            LibraryRepository(membership), DownloadRepository(DesktopDownloadDao(library)))
        try {
            reader.open(library); val old = reader.state.value.projection
            reader.download(); withTimeout(5_000) { reader.state.first { !it.busy } }
            assertTrue(reader.state.value.error!!.contains("could not add the novel"))
            assertNotSame(old, reader.state.value.projection)
            assertTrue(DesktopSavedChapterDao(library).isObservationCurrent(reader.state.value.storageObservation!!))
            reader.position(fixtureKey, 1, 9, reader.state.value.projection!!); reader.flushPosition()
            assertNotNull(DesktopReadingCheckpointDao(library).read(fixtureKey)); assertFalse(library.book(fixtureKey.novel)!!.favorite)
        } finally { reader.close() }
    }
}
