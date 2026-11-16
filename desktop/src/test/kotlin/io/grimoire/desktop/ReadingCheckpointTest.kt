package io.grimoire.desktop

import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReadingCheckpointTest {
    private fun java.sql.Connection.value(sql: String) = query(sql) { it.getLong(1) }.single()
    @get:Rule val temporary = TemporaryFolder()
    private val document = CanonicalDocument.fromPages(fixtureKey, prose())
    private fun command(session: ReadingSession, sequence: Long = 1, position: AnchorPosition = AnchorPosition.Start) =
        CheckpointCommand(session, sequence, ReadingAnchor(session.document.key, session.document.logicalRevision, position))
    private suspend fun session(library: LocalLibrary): ReadingSession = requireNotNull(DesktopReadingCheckpointDao(library).register(
        requireNotNull(DesktopSavedChapterDao(library).readObserved(fixtureKey).observation), document))

    @Test fun sequenceRetryConflictAndBackwardMovementPreserveAllLegacyState() = runBlocking<Unit> {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); library.download(fixtureKey, prose()); library.position(fixtureKey, 0, 17); library.flush()
            val before = library.book(fixtureKey.novel)
            val bytes = DesktopSavedChapterDao(library).readSnapshot(fixtureKey)
            val receipt = DesktopDownloadDao(library).currentReceipt(fixtureKey)
            val dao = DesktopReadingCheckpointDao(library)
            assertNull(dao.read(fixtureKey))
            val s = session(library)
            val end = command(s, 8, AnchorPosition.End)
            assertEquals(CheckpointCommit.Committed(end.anchor, 8, false), dao.commit(end))
            val backward = command(s, 9)
            assertEquals(CheckpointCommit.Committed(backward.anchor, 9, false), dao.commit(backward))
            assertEquals(CheckpointCommit.Committed(backward.anchor, 9, true), dao.commit(backward))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.CONFLICT), dao.commit(command(s, 9, AnchorPosition.End)))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(end))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.INVALID), dao.commit(command(s, 0)))
            assertEquals(backward.anchor, dao.read(fixtureKey))
            assertEquals(before, library.book(fixtureKey.novel)); assertEquals(bytes, DesktopSavedChapterDao(library).readSnapshot(fixtureKey))
            assertEquals(receipt, DesktopDownloadDao(library).currentReceipt(fixtureKey))
        }
    }

    @Test fun newSessionRevokesOldWriterWithoutErasingCheckpointAndChaptersStayIndependent() = runBlocking<Unit> {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed()
            val secondKey = fixtureKey.copy(url = "/second")
            library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters + io.grimoire.api.model.novel.Chapter(secondKey.url, "Second"))
            val dao = DesktopReadingCheckpointDao(library)
            val first = session(library); val saved = command(first)
            dao.commit(saved)
            val otherDoc = CanonicalDocument.fromPages(secondKey, prose())
            val other = requireNotNull(dao.register(requireNotNull(DesktopSavedChapterDao(library).readObserved(secondKey).observation), otherDoc))
            assertTrue(dao.commit(command(other)) is CheckpointCommit.Committed)
            val current = session(library)
            assertEquals(saved.anchor, dao.read(fixtureKey))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(saved))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.INVALID), dao.commit(command(current).copy(anchor = command(other).anchor)))
            assertNull(dao.register(current.observation, otherDoc))
            assertNull(dao.register(current.observation, CanonicalDocument.fromPages(fixtureKey, emptyList())))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(command(current.copy(id = "invented"))))
            assertTrue(dao.commit(command(current)) is CheckpointCommit.Committed)
            assertTrue(dao.commit(command(other, 2)) is CheckpointCommit.Committed)
        }
    }

    @Test fun sourceAbsenceContentAbaRelockAndRemovalInvalidateRegistrationAndEvenExactReplay() = runBlocking<Unit> {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); val dao = DesktopReadingCheckpointDao(library)
            var s = session(library); dao.commit(command(s))
            val mutations: List<() -> Unit> = listOf(
                { library.download(fixtureKey, prose()) }, { library.download(fixtureKey, prose()) },
                { library.download(fixtureKey, prose("Different")); library.download(fixtureKey, prose()) },
                { library.deleteDownload(fixtureKey) },
                { library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters.map { it.copy(locked = true) }); library.seed() },
                { library.remember(fixtureKey.novel, fixtureNovel, emptyList()); library.seed() })
            for (mutate in mutations) {
                mutate()
                assertNull(dao.register(s.observation, document))
                assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(command(s)))
                s = session(library); assertTrue(dao.commit(command(s)) is CheckpointCommit.Committed)
            }
        }
    }

    @Test fun reopenRetainsPortableCheckpointButRevokesAllPreviousOpenAuthority() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory); library.seed()
        val previous = session(library); val written = command(previous, 3)
        DesktopReadingCheckpointDao(library).commit(written); library.close()
        LocalLibrary(directory).use { reopened ->
            val dao = DesktopReadingCheckpointDao(reopened)
            assertEquals(written.anchor, dao.read(fixtureKey))
            assertNull(dao.register(previous.observation, document))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(written))
            assertTrue(dao.commit(command(session(reopened))) is CheckpointCommit.Committed)
        }
    }

    @Test fun rollbackAndLostReplyAreDistinguishedByIdenticalRetry() = runBlocking<Unit> {
        var fault: StorageCheckpoint? = null
        LocalLibrary(temporary.newFolder().toPath()) { if (it == fault) throw IOException("Injected checkpoint fault") }.use { library ->
            library.seed(); val dao = DesktopReadingCheckpointDao(library); val original = session(library)
            fault = StorageCheckpoint.BEFORE_COMMIT
            try { dao.register(original.observation, document); fail("registration should roll back") } catch (e: IOException) { assertTrue(e.message!!.contains("Injected")) }
            fault = null
            assertTrue(dao.commit(command(original)) is CheckpointCommit.Committed)
            fault = StorageCheckpoint.BEFORE_COMMIT
            try { dao.commit(command(original, 2, AnchorPosition.End)); fail("write should roll back") } catch (e: IOException) { assertTrue(e.message!!.contains("Injected")) }
            fault = null
            assertEquals(command(original).anchor, dao.read(fixtureKey))
            fault = StorageCheckpoint.AFTER_COMMIT
            val uncertain = command(original, 2, AnchorPosition.End)
            try { dao.commit(uncertain); fail("reply should be lost") } catch (e: IOException) { assertTrue(e.message!!.contains("Injected")) }
            fault = null
            assertEquals(CheckpointCommit.Committed(uncertain.anchor, 2, true), dao.commit(uncertain))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.CONFLICT), dao.commit(command(original, 2)))
        }
    }

    @Test fun cancellationBeforeCommitRollsBackAndKeepsPreviousSessionUsable() = runBlocking<Unit> {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); var armed = false
        LocalLibrary(temporary.newFolder().toPath()) {
            if (armed && it == StorageCheckpoint.BEFORE_COMMIT) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        }.use { library ->
            library.seed(); val dao = DesktopReadingCheckpointDao(library); val old = session(library)
            armed = true
            val job = launch(Dispatchers.Default) { dao.commit(command(old)) }
            try { check(entered.await(5, TimeUnit.SECONDS)); job.cancel() } finally { armed = false; release.countDown() }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(dao.commit(command(old)) is CheckpointCommit.Committed)
        }
    }

    @Test fun sql1UpgradeIsAtomicAndRetainsLegacyBodyMetadataAndDownloadProtocol() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        lateinit var before: SavedBook
        lateinit var body: SavedChapterSnapshot
        var receipt: DownloadReceipt? = null
        LocalLibrary(directory).use { library ->
            library.seed(); library.download(fixtureKey, prose()); library.position(fixtureKey, 0, 17); library.flush()
            before = library.book(fixtureKey.novel)!!; body = DesktopSavedChapterDao(library).readSnapshot(fixtureKey)!!
            receipt = DesktopDownloadDao(library).currentReceipt(fixtureKey)
        }
        mutateDatabase(directory, "DROP TABLE reading_checkpoints"); mutateDatabase(directory, "PRAGMA user_version=1")
        try { LocalLibrary(directory) { if (it == StorageCheckpoint.BEFORE_COMMIT) throw IOException("Migration fault") }; fail("upgrade must roll back") }
        catch (e: IOException) { assertEquals("Migration fault", e.message) }
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { db ->
            assertEquals(1L, db.value("PRAGMA user_version"))
            assertEquals(0L, db.value("SELECT count(*) FROM sqlite_master WHERE name='reading_checkpoints'"))
        }
        LocalLibrary(directory).use { library ->
            assertEquals(before, library.book(fixtureKey.novel)); assertEquals(body, DesktopSavedChapterDao(library).readSnapshot(fixtureKey))
            assertEquals(receipt, DesktopDownloadDao(library).currentReceipt(fixtureKey)); assertNull(DesktopReadingCheckpointDao(library).read(fixtureKey))
        }
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { assertEquals(2L, it.value("PRAGMA user_version")) }
    }
}
