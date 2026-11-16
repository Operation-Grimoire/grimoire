package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ChapterObservationTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val pages = listOf(NovelPage(0, PageContent.Text("Original")))
    private fun stamp(generation: String, presence: ChapterContentPresence) =
        ChapterStorageObservation(key, "store", generation, presence)

    @Test fun legacyAdaptersAndKeylessLoadsNeverInventObservationsFromCacheIdentity() = runBlocking {
        val dao = object : SavedChapterDao {
            override suspend fun read(key: ChapterKey) = encodeChapterContent(pages)
            override suspend fun readSnapshot(key: ChapterKey) = SavedChapterSnapshot(read(key), "image-cache")
        }
        val repository = SavedChapterRepository(dao)
        val loader = ChapterLoader(repository)
        val saved = loader.load(key) { error("Saved content wins") }
        assertEquals("image-cache", saved.cacheRevision)
        assertNull(saved.storageObservation)
        assertFalse(repository.isObservationCurrent(stamp("made-up", ChapterContentPresence.SAVED)))
        val keyless = ChapterLoader(SavedChapterRepository { error("No identity") })
            .load(null) { ChapterPageAccess.Available { pages } }
        assertNull(keyless.storageObservation)
        val absent = ChapterLoader(SavedChapterRepository { null }).load(key, pages) { error("Preload wins") }
        assertNull(absent.storageObservation)
    }

    @Test fun emptySavedContentAndPreloadCarryDifferentExplicitPresence() = runBlocking {
        val absent = stamp("a", ChapterContentPresence.ABSENT)
        val dao = ObservedDao(ObservedSavedChapter(null, absent))
        val loader = ChapterLoader(SavedChapterRepository(dao))
        val preloaded = loader.load(key, pages) { error("Preload wins") }
        assertEquals(ChapterOrigin.PRELOADED, preloaded.origin)
        assertSame(pages, preloaded.pages)
        assertEquals(absent, preloaded.storageObservation)
        val present = stamp("b", ChapterContentPresence.SAVED)
        dao.current = ObservedSavedChapter(SavedChapterSnapshot(encodeChapterContent(emptyList())), present)
        val saved = loader.load(key, pages) { error("Empty saved content wins") }
        assertEquals(ChapterOrigin.SAVED, saved.origin)
        assertTrue(saved.pages.isEmpty())
        assertEquals(present, saved.storageObservation)
        assertNull(saved.cacheRevision)
    }

    @Test fun sourceLoadRetainsTheEarlierAbsenceWhenStorageChangesDuringFetch() = runBlocking {
        val absent = stamp("before-fetch", ChapterContentPresence.ABSENT)
        val present = stamp("after-fetch", ChapterContentPresence.SAVED)
        val dao = ObservedDao(ObservedSavedChapter(null, absent))
        val repository = SavedChapterRepository(dao)
        val loader = ChapterLoader(repository)
        val source = loader.load(key) {
            assertEquals(1, dao.observations)
            ChapterPageAccess.Available {
                dao.current = ObservedSavedChapter(SavedChapterSnapshot(encodeChapterContent(pages)), present)
                pages
            }
        }
        assertEquals(ChapterOrigin.SOURCE, source.origin)
        assertEquals(absent, source.storageObservation)
        assertFalse(repository.isObservationCurrent(requireNotNull(source.storageObservation)))
        val saved = loader.load(key) { error("Now saved") }
        assertEquals(present, saved.storageObservation)
        assertTrue(repository.isObservationCurrent(present))
        assertEquals(CanonicalDocument.fromPages(key, source.pages).logicalRevision,
            CanonicalDocument.fromPages(key, saved.pages).logicalRevision)
    }

    @Test fun wrongIdentityAndInconsistentPresenceAreRejectedBeforeFallback() = runBlocking<Unit> {
        val wrong = stamp("a", ChapterContentPresence.ABSENT).copy(key = key.copy(url = "/other"))
        val loader = ChapterLoader(SavedChapterRepository(ObservedDao(ObservedSavedChapter(null, wrong))))
        try { loader.load(key, pages) { error("Must not fall back") }; fail("Accepted wrong key") }
        catch (_: IllegalArgumentException) { }
        assertThrows(IllegalArgumentException::class.java) {
            ObservedSavedChapter(null, stamp("a", ChapterContentPresence.SAVED))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObservedSavedChapter(SavedChapterSnapshot(encodeChapterContent(pages)), stamp("a", ChapterContentPresence.ABSENT))
        }
    }

    @Test fun observationFailureAndNonCooperativeCancellationCannotLeakPagesOrStartFallback() = runBlocking {
        val failure = IOException("Protocol identity unavailable")
        val dao = object : SavedChapterDao {
            override suspend fun read(key: ChapterKey) = encodeChapterContent(pages)
            override suspend fun readObserved(key: ChapterKey): ObservedSavedChapter = throw failure
        }
        try { ChapterLoader(SavedChapterRepository(dao)).load(key, pages) { error("No fallback") }; fail("Hidden failure") }
        catch (actual: IOException) { assertSame(failure, actual) }
        var result: Result<LoadedChapter>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            val late = object : SavedChapterDao {
                override suspend fun read(key: ChapterKey): StoredChapterContent? = error("Observed read only")
                override suspend fun readObserved(key: ChapterKey): ObservedSavedChapter {
                    currentCoroutineContext().cancel()
                    return ObservedSavedChapter(null, stamp("late", ChapterContentPresence.ABSENT))
                }
            }
            result = runCatching { ChapterLoader(SavedChapterRepository(late)).load(key) { error("Cancelled fallback") } }
        }
        job.join()
        assertTrue(result?.exceptionOrNull() is CancellationException)
    }

    private class ObservedDao(var current: ObservedSavedChapter) : SavedChapterDao {
        var observations = 0
        override suspend fun read(key: ChapterKey): StoredChapterContent? = error("Observed path required")
        override suspend fun readObserved(key: ChapterKey): ObservedSavedChapter { observations++; return current }
        override suspend fun isObservationCurrent(observation: ChapterStorageObservation) = current.observation == observation
    }
}
