package io.grimoire.reading.storage

import io.grimoire.reading.NovelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LibraryRepositoryTest {
    private val key = NovelKey(17, "/book")
    private val original = LibraryEntry(key, "Title", false, "", null)

    private class MemoryDao(var entry: LibraryEntry?) : LibraryDao {
        val writes = mutableListOf<Pair<NovelKey, Boolean>>()
        override suspend fun find(key: NovelKey) = entry?.takeIf { it.key == key }
        override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? {
            writes += key to inLibrary
            return find(key)?.copy(inLibrary = inLibrary)?.also { entry = it }
        }
    }

    @Test fun desiredMembershipIsIdempotentAndPreservesMetadata() = runBlocking {
        val dao = MemoryDao(original)
        val repository = LibraryRepository(dao)
        assertEquals(original, repository.find(key))
        for (value in listOf(true, true, false, false)) {
            assertEquals(original.copy(inLibrary = value), repository.setMembership(key, value))
        }
        assertEquals(listOf(true, true, false, false), dao.writes.map { it.second })
    }

    @Test fun absentAndOtherSourceRecordsAreNeverCreatedOrModified() = runBlocking {
        val dao = MemoryDao(original)
        val repository = LibraryRepository(dao)
        for (missing in listOf(NovelKey(18, key.url), NovelKey(17, "/missing"))) {
            assertNull(repository.find(missing))
            assertNull(repository.setMembership(missing, true))
            assertEquals(original, dao.entry)
        }
        dao.entry = null
        assertNull(repository.setMembership(key, true))
        assertNull(dao.entry)
    }

    @Test fun invalidBackendIdentityAndUnconfirmedMembershipAreRejected() = runBlocking {
        for (response in listOf(original.copy(key = NovelKey(18, key.url)), original)) {
            val repository = LibraryRepository(object : LibraryDao {
                override suspend fun find(key: NovelKey) = response
                override suspend fun setMembership(key: NovelKey, inLibrary: Boolean) = response
            })
            if (response.key != key) {
                try { repository.find(key); fail("Wrong identity accepted") }
                catch (_: IllegalStateException) { }
            }
            try { repository.setMembership(key, true); fail("Invalid confirmation accepted") }
            catch (_: IllegalStateException) { }
        }
    }

    @Test fun storageAccessAndCancellationFailuresPropagateWithoutRetry() = runBlocking {
        for (failure in listOf(IOException("disk"), SecurityException("denied"), CancellationException("cancelled"))) {
            var writes = 0
            val repository = LibraryRepository(object : LibraryDao {
                override suspend fun find(key: NovelKey): LibraryEntry? = throw failure
                override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? {
                    writes++
                    throw failure
                }
            })
            try { repository.find(key); fail("Read failure hidden") }
            catch (actual: Exception) { assertSame(failure, actual) }
            try { repository.setMembership(key, true); fail("Write failure hidden") }
            catch (actual: Exception) { assertSame(failure, actual) }
            assertEquals(1, writes)
        }
    }

    @Test fun retryAfterLostConfirmationSetsTheSameValueInsteadOfReversingIt() = runBlocking {
        val dao = MemoryDao(original)
        var loseConfirmation = true
        val repository = LibraryRepository(object : LibraryDao {
            override suspend fun find(key: NovelKey) = dao.find(key)
            override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? {
                val result = dao.setMembership(key, inLibrary)
                if (loseConfirmation) { loseConfirmation = false; throw CancellationException("after commit") }
                return result
            }
        })
        try { repository.setMembership(key, true); fail("Cancellation hidden") }
        catch (_: CancellationException) { }
        assertTrue(dao.entry!!.inLibrary)
        assertTrue(repository.setMembership(key, true)!!.inLibrary)
        assertEquals(listOf(true, true), dao.writes.map { it.second })
    }
}
