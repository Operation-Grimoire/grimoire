package io.grimoire.desktop

import io.grimoire.api.model.novel.*
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class DesktopStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun commitReplayConflictSupersessionFailureAndDeleteThroughSharedRepository() = runBlocking {
        LocalLibrary(temporary.newFolder().toPath()).use { store ->
            store.seed()
            val repository = DownloadRepository(DesktopDownloadDao(store))
            val first = repository.claim(fixtureKey)!!
            val second = repository.claim(fixtureKey)!!
            assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(first, prose("stale")))
            assertFalse(repository.fail(first, DownloadFailure.ERROR))
            val committed = repository.commit(second, prose()) as DownloadCommit.Committed
            assertFalse(committed.replayed)
            assertEquals(2, committed.receipt.wordCount)
            assertEquals(committed.receipt, repository.currentReceipt(fixtureKey))
            assertEquals(committed.copy(replayed = true), repository.commit(second, prose()))
            try { repository.commit(second, prose("conflicting")); fail("Conflicting retry accepted") } catch (_: IllegalArgumentException) { }
            val refresh = repository.claim(fixtureKey)!!
            assertTrue(repository.fail(refresh, DownloadFailure.CONNECTIVITY))
            assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(refresh, prose("cancelled")))
            assertEquals(prose(), store.downloaded(fixtureKey))
            val pending = repository.claim(fixtureKey)!!
            store.deleteDownload(fixtureKey)
            assertNull(repository.currentReceipt(fixtureKey))
            assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(second, prose()))
            assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(pending, prose()))
            assertNull(store.downloaded(fixtureKey))
            assertTrue(store.book(fixtureKey.novel)!!.downloaded.isEmpty())
        }
    }

    @Test fun metadataRemovalRelockAndRecreationRevokeAttemptsAndReplayReceipts() = runBlocking {
        LocalLibrary(temporary.newFolder().toPath()).use { store ->
            store.seed()
            val repository = DownloadRepository(DesktopDownloadDao(store))
            val saved = repository.claim(fixtureKey)!!
            repository.commit(saved, prose())
            for (chapters in listOf(emptyList(), fixtureChapters.map { it.copy(locked = true) })) {
                val attempt = repository.claim(fixtureKey)!!
                store.remember(fixtureKey.novel, fixtureNovel, chapters)
                assertNull(repository.currentReceipt(fixtureKey))
                val reason = if (chapters.isEmpty()) DownloadRejection.MISSING else DownloadRejection.LOCKED
                assertEquals(DownloadCommit.NotApplied(reason), repository.commit(attempt, prose("late")))
                try { store.downloaded(fixtureKey); fail("Unavailable chapter was read") } catch (_: IllegalArgumentException) { }
                store.seed()
                assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(attempt, prose("late")))
                assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(saved, prose()))
                assertEquals(prose(), store.downloaded(fixtureKey))
            }
        }
    }

    @Test fun realSqlRollbackAndLostReplyKeepBodyIndexAndReceiptCoherentAcrossReopen() = runBlocking {
        val directory = temporary.newFolder().toPath()
        var inject: StorageCheckpoint? = null
        lateinit var receipt: DownloadReceipt
        lateinit var attempt: DownloadAttempt
        LocalLibrary(directory) { if (it == inject) throw IOException("injected $it") }.use { store ->
            store.seed()
            val repository = DownloadRepository(DesktopDownloadDao(store))
            val original = repository.claim(fixtureKey)!!
            receipt = (repository.commit(original, prose("old")) as DownloadCommit.Committed).receipt
            attempt = repository.claim(fixtureKey)!!
            inject = StorageCheckpoint.BEFORE_COMMIT
            try { repository.commit(attempt, prose("new")); fail("Delivered") } catch (_: IOException) { }
            assertEquals(prose("old"), store.downloaded(fixtureKey))
            assertEquals(receipt, repository.currentReceipt(fixtureKey))
            inject = StorageCheckpoint.AFTER_COMMIT
            try { repository.commit(attempt, prose("new")); fail("Delivered") } catch (_: IOException) { }
            assertEquals(prose("new"), store.downloaded(fixtureKey))
            inject = null
        }
        LocalLibrary(directory).use { store ->
            val repository = DownloadRepository(DesktopDownloadDao(store))
            val replay = repository.commit(attempt, prose("new")) as DownloadCommit.Committed
            assertTrue(replay.replayed)
            assertNotEquals(receipt.revision, replay.receipt.revision)
            assertEquals(setOf("/chapter"), store.book(fixtureKey.novel)!!.downloaded)
            assertEquals(replay.receipt.revision, DesktopSavedChapterDao(store).readSnapshot(fixtureKey)!!.cacheRevision)
        }
    }

    @Test fun cancellationObservedInsideSqlTransactionRollsBackButAfterCommitCanLoseReply() = runBlocking {
        val directory = temporary.newFolder().toPath()
        var cancelAt: StorageCheckpoint? = null
        var operation: Job? = null
        LocalLibrary(directory) { if (it == cancelAt) operation!!.cancel() }.use { store ->
            store.seed()
            val repository = DownloadRepository(DesktopDownloadDao(store))
            val attempt = repository.claim(fixtureKey)!!
            for (point in listOf(StorageCheckpoint.BEFORE_COMMIT, StorageCheckpoint.AFTER_COMMIT)) {
                cancelAt = point
                val deferred = async(start = CoroutineStart.LAZY) { repository.commit(attempt, prose()) }
                operation = deferred
                deferred.start()
                try { deferred.await(); fail("Cancelled reply delivered") } catch (_: CancellationException) { }
                cancelAt = null
                if (point == StorageCheckpoint.BEFORE_COMMIT) {
                    assertNull(repository.currentReceipt(fixtureKey)); assertNull(store.downloaded(fixtureKey))
                } else {
                    assertEquals(attempt.id, repository.currentReceipt(fixtureKey)!!.attemptId)
                    assertEquals(prose(), store.downloaded(fixtureKey))
                    assertTrue((repository.commit(attempt, prose()) as DownloadCommit.Committed).replayed)
                }
            }
        }
    }

    @Test fun sharedLoadingScenariosThroughRealSqlAdapterNeverRepairCorruptOrEmptySavedContent() = runBlocking {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { store ->
            store.seed()
            store.download(fixtureKey, prose())
            val loader = ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(store)))
            var resolutions = 0
            val source: suspend () -> ChapterPageAccess = { resolutions++; ChapterPageAccess.Available { prose("network") } }
            assertEquals(ChapterOrigin.SAVED, loader.load(fixtureKey, emptyList(), source).origin)
            val empty = encodeChapterContent(emptyList())
            mutateDatabase(directory, "UPDATE contents SET content=?,content_version=?,digest=?", empty.content, empty.version, downloadDigest(empty))
            assertTrue(loader.load(fixtureKey, prose(), source).pages.isEmpty())
            mutateDatabase(directory, "UPDATE contents SET content_version=99")
            try { loader.load(fixtureKey, prose(), source); fail("Corrupt content repaired") } catch (_: IllegalArgumentException) { }
            assertEquals(0, resolutions)
            val missing = fixtureKey.copy(url = "/missing")
            assertEquals(ChapterOrigin.PRELOADED, loader.load(missing, emptyList(), source).origin)
            assertEquals(ChapterOrigin.SOURCE, loader.load(missing, sourceAccess = source).origin)
            assertEquals(1, resolutions)
            try { loader.load(missing) { ChapterPageAccess.Unavailable(ChapterLoadFailure.OFFLINE) }; fail("Offline missing loaded") }
            catch (failure: ChapterLoadException) { assertEquals(ChapterLoadFailure.OFFLINE, failure.reason) }
        }
    }

    @Test fun readerSurfacesStorageFailureInsteadOfTryingSource() = runBlocking {
        val store = LocalLibrary(temporary.newFolder().toPath()).apply { seed() }
        val model = ReaderModel(store, "1.5.0", AtomicBoolean(false), ChapterLoader(SavedChapterRepository { throw IOException("saved chapter disk failure") }),
            LibraryRepository(DesktopLibraryDao(store)), DownloadRepository(DesktopDownloadDao(store)))
        try {
            model.openSaved(store.book(fixtureKey.novel)!!); model.settled()
            model.read(fixtureChapters.first()); model.settled()
            assertEquals("saved chapter disk failure", model.state.value.error)
            assertTrue(model.state.value.pages.isEmpty())
        } finally { model.close() }
    }

    @Test fun readerReportsSavedContentSeparatelyFromMembershipFailureAndReconcilesLostCommitReply() = runBlocking {
        var inject = false
        val store = LocalLibrary(temporary.newFolder().toPath()) { if (inject && it == StorageCheckpoint.AFTER_COMMIT) { inject = false; throw IOException("reply lost") } }.apply { seed() }
        val failingMembership = object : LibraryDao {
            override suspend fun find(key: NovelKey): LibraryEntry? = null
            override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? = throw IOException("membership unavailable")
        }
        val delegate = DesktopDownloadDao(store)
        val downloads = object : DownloadDao by delegate {
            override suspend fun commit(attempt: DownloadAttempt, prepared: PreparedDownload): DownloadCommit { inject = true; return delegate.commit(attempt, prepared) }
        }
        val model = ReaderModel(store, "1.5.0", AtomicBoolean(false), ChapterLoader(SavedChapterRepository { encodeChapterContent(prose()) }),
            LibraryRepository(failingMembership), DownloadRepository(downloads))
        try {
            model.openSaved(store.book(fixtureKey.novel)!!); model.settled()
            model.read(fixtureChapters.first()); model.settled()
            model.download(); model.settled()
            assertTrue(model.state.value.error!!.startsWith("Chapter saved, but could not add"))
            assertEquals(setOf("/chapter"), model.state.value.book!!.downloaded)
            assertEquals(prose(), store.downloaded(fixtureKey))
            assertFalse(store.book(fixtureKey.novel)!!.favorite)
        } finally { model.close() }
    }

    @Test fun chapterLookupAndCommitReadOnlyTheRequestedNovel() = runBlocking {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { store ->
            store.seed()
            store.remember(NovelKey(18, "/novel"), fixtureNovel, fixtureChapters)
            val otherMetadata = java.sql.DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { raw ->
                raw.query("SELECT metadata FROM books WHERE source_id=18") { it.getString(1) }.single()
            }
            mutateDatabase(directory, "UPDATE books SET metadata='{broken' WHERE source_id=18")
            try {
                assertEquals(fixtureNovel.title, store.book(fixtureKey.novel)!!.novel.title)
                val repository = DownloadRepository(DesktopDownloadDao(store))
                assertTrue(repository.commit(repository.claim(fixtureKey)!!, prose()) is DownloadCommit.Committed)
                assertEquals(prose(), store.downloaded(fixtureKey))
            } finally {
                mutateDatabase(directory, "UPDATE books SET metadata=? WHERE source_id=18", otherMetadata)
            }
        }
    }

    private suspend fun ReaderModel.settled() { withTimeout(5_000) { state.first { !it.busy } } }
}
