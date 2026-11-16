package io.grimoire.desktop

import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager

class ChapterObservationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun absentObservationAllocatesOnlyProtocolIdentityAndRepeatedReadsKeepIt() = runBlocking {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { library ->
            library.seed(); library.position(fixtureKey, 0, 17); library.flush()
            val before = library.book(fixtureKey.novel)
            val dao = DesktopSavedChapterDao(library)
            assertNull(dao.readSnapshot(fixtureKey))
            assertEquals(0, rows(directory))
            val read = dao.readObserved(fixtureKey)
            val observation = requireNotNull(read.observation)
            assertNull(read.saved)
            assertEquals(ChapterContentPresence.ABSENT, observation.presence)
            assertTrue(dao.isObservationCurrent(observation))
            assertEquals(read, dao.readObserved(fixtureKey))
            assertEquals(before, library.book(fixtureKey.novel))
            assertEquals(1, rows(directory))
            assertNull(dao.readObserved(fixtureKey.copy(url = "/missing")).observation)
            assertNull(dao.readObserved(fixtureKey.copy(url = "/locked")).observation)
            assertFalse(dao.isObservationCurrent(observation.copy(key = fixtureKey.copy(url = "/missing"))))
            assertEquals(1, rows(directory))
        }
    }

    @Test fun legacySavedBytesAndPositionDoNotBecomeAReceiptOrGetRewritten() = runBlocking {
        val directory = temporary.newFolder().toPath()
        legacyFixture(directory)
        LocalLibrary(directory).use { library ->
            val dao = DesktopSavedChapterDao(library)
            val before = library.book(fixtureKey.novel)
            val saved = requireNotNull(dao.readSnapshot(fixtureKey))
            assertEquals(1, saved.content.version)
            val read = dao.readObserved(fixtureKey)
            assertEquals(saved, read.saved)
            assertEquals(ChapterContentPresence.SAVED, read.observation!!.presence)
            assertTrue(dao.isObservationCurrent(read.observation!!))
            assertNull(library.currentReceipt(fixtureKey))
            assertEquals(before, library.book(fixtureKey.novel))
        }
    }

    @Test fun identicalContentAbaRelockAndChapterRemovalNeverReviveOldObservations() = runBlocking {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); library.download(fixtureKey, prose())
            val dao = DesktopSavedChapterDao(library)
            val old = requireNotNull(dao.readObserved(fixtureKey).observation)
            library.deleteDownload(fixtureKey)
            assertFalse(dao.isObservationCurrent(old))
            val absent = requireNotNull(dao.readObserved(fixtureKey).observation)
            library.download(fixtureKey, prose())
            assertFalse(dao.isObservationCurrent(old)); assertFalse(dao.isObservationCurrent(absent))
            val fresh = requireNotNull(dao.readObserved(fixtureKey).observation)
            library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters.map { it.copy(locked = true) })
            assertFalse(dao.isObservationCurrent(fresh))
            library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters)
            assertFalse(dao.isObservationCurrent(fresh))
            val unlocked = requireNotNull(dao.readObserved(fixtureKey).observation)
            library.remember(fixtureKey.novel, fixtureNovel, emptyList())
            assertFalse(dao.isObservationCurrent(unlocked))
            library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters)
            assertFalse(dao.isObservationCurrent(unlocked))
            assertTrue(dao.isObservationCurrent(requireNotNull(dao.readObserved(fixtureKey).observation)))
        }
    }

    @Test fun observationDuringRefreshPreservesTheActiveAttemptAndOnlyCommitInvalidatesIt() = runBlocking {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); library.download(fixtureKey, prose())
            val dao = DesktopSavedChapterDao(library)
            val before = requireNotNull(dao.readObserved(fixtureKey).observation)
            val repository = DownloadRepository(DesktopDownloadDao(library))
            val attempt = requireNotNull(repository.claim(fixtureKey))
            assertEquals(before, dao.readObserved(fixtureKey).observation)
            assertTrue(dao.isObservationCurrent(before))
            assertTrue(repository.commit(attempt, prose("New text")) is DownloadCommit.Committed)
            assertFalse(dao.isObservationCurrent(before))
        }
    }

    @Test fun retainedContentOfLockedOrRemovedChaptersCannotFallBackToPreloadOrSource() = runBlocking {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); library.download(fixtureKey, prose())
            val dao = DesktopSavedChapterDao(library)
            val loader = ChapterLoader(SavedChapterRepository(dao))
            val old = requireNotNull(dao.readObserved(fixtureKey).observation)
            var sourceCalls = 0
            for (chapters in listOf(fixtureChapters.map { it.copy(locked = true) }, emptyList())) {
                library.remember(fixtureKey.novel, fixtureNovel, chapters)
                assertFalse(dao.isObservationCurrent(old))
                for (preloaded in listOf(null, prose("Must not bypass access failure"))) {
                    try {
                        loader.load(fixtureKey, preloaded) {
                            sourceCalls++
                            ChapterPageAccess.Available { error("Must not fetch blocked content") }
                        }
                        fail("Blocked saved content was reclassified as absent")
                    } catch (failure: IllegalArgumentException) {
                        assertTrue(failure.message.orEmpty().contains("unavailable or locked"))
                    }
                }
            }
            assertEquals(0, sourceCalls)
        }
    }

    @Test fun reopenAndOtherStoresCannotReuseTheObserversScope() = runBlocking {
        val directory = temporary.newFolder().toPath()
        val first = LocalLibrary(directory).apply { seed() }
        val dao = DesktopSavedChapterDao(first)
        val old = requireNotNull(dao.readObserved(fixtureKey).observation)
        first.close()
        assertFalse(dao.isObservationCurrent(old))
        LocalLibrary(directory).use { reopened ->
            val next = DesktopSavedChapterDao(reopened)
            val current = requireNotNull(next.readObserved(fixtureKey).observation)
            assertEquals(old.generation, current.generation)
            assertNotEquals(old.scope, current.scope)
            assertFalse(next.isObservationCurrent(old)); assertTrue(next.isObservationCurrent(current))
        }
        LocalLibrary(temporary.newFolder().toPath()).use { other ->
            other.seed()
            assertFalse(DesktopSavedChapterDao(other).isObservationCurrent(old))
        }
    }

    @Test fun allocationFailureRollsBackWithoutChangingContentOrLegacyState() = runBlocking {
        val directory = temporary.newFolder().toPath()
        var armed = false
        val failure = java.io.IOException("Before commit")
        LocalLibrary(directory) { if (armed && it == StorageCheckpoint.BEFORE_COMMIT) throw failure }.use { library ->
            library.seed()
            val before = library.book(fixtureKey.novel)
            val dao = DesktopSavedChapterDao(library)
            armed = true
            try { dao.readObserved(fixtureKey); fail("Allocation succeeded") }
            catch (actual: java.io.IOException) {
                assertEquals(failure.message, actual.message)
                assertTrue(actual === failure || actual.cause === failure) // Coroutine stack recovery can copy IOException.
            }
            armed = false
            assertEquals(0, rows(directory)); assertEquals(before, library.book(fixtureKey.novel))
            assertTrue(dao.isObservationCurrent(requireNotNull(dao.readObserved(fixtureKey).observation)))
        }
    }

    private fun rows(directory: java.nio.file.Path): Int =
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { db ->
            db.createStatement().use { statement -> statement.executeQuery("SELECT COUNT(*) FROM contents").use { it.next(); it.getInt(1) } }
        }
}
