package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.storage.SavedChapterRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ChapterLoaderTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val pages = listOf(NovelPage(0, PageContent.Text("Original")))

    @Test fun savedContentWinsOverPreloadedAndUnavailableSourcesIncludingEmptyContent() = runBlocking {
        for (record in listOf(encodeChapterContent(pages), encodeChapterContent(emptyList()), StoredChapterContent("", 1))) {
            val loader = ChapterLoader(SavedChapterRepository { requested -> assertEquals(key, requested); record })
            val result = loader.load(key, pages) { error("Must not resolve source access") }
            assertEquals(ChapterOrigin.SAVED, result.origin)
            assertEquals(encodeChapterContent(decodeChapterContent(record)), encodeChapterContent(result.pages))
        }
    }

    @Test fun preloadedContentWinsOnlyWhenSavedContentIsAbsentIncludingEmptyPreload() = runBlocking {
        val loader = ChapterLoader(SavedChapterRepository { null })
        for (preloaded in listOf(pages, emptyList())) {
            val result = loader.load(key, preloaded) { error("Must not resolve source access") }
            assertEquals(ChapterOrigin.PRELOADED, result.origin)
            assertSame(preloaded, result.pages)
        }
    }

    @Test fun absentContentResolvesSourceOnceAndReturnsOriginalUnfilteredPages() = runBlocking {
        val original = pages + NovelPage(1, PageContent.Text("")) + NovelPage(2, PageContent.Image("remote"))
        var resolutions = 0
        var reads = 0
        val loader = ChapterLoader(SavedChapterRepository { null })
        val result = loader.load(key) {
            resolutions++
            ChapterPageAccess.Available { reads++; original }
        }
        assertEquals(ChapterOrigin.SOURCE, result.origin)
        assertSame(original, result.pages)
        assertEquals(1, resolutions)
        assertEquals(1, reads)
    }

    @Test fun missingStoredNovelIdentitySkipsStorageWithoutInventingAKey() = runBlocking {
        val loader = ChapterLoader(SavedChapterRepository { error("No stored identity") })
        assertSame(pages, loader.load(null) { ChapterPageAccess.Available { pages } }.pages)
    }

    @Test fun offlineMissingSourceAndUnsupportedPagesAreDistinctAndDoNotReadSources() = runBlocking {
        val loader = ChapterLoader(SavedChapterRepository { null })
        for (reason in ChapterLoadFailure.entries) {
            try {
                loader.load(key) { ChapterPageAccess.Unavailable(reason) }
                fail("Unavailable source was used")
            } catch (failure: ChapterLoadException) { assertEquals(reason, failure.reason) }
        }
    }

    @Test fun corruptOrUnsupportedSavedContentDoesNotFallBackEvenToPreloadedPages() = runBlocking {
        for (record in listOf(StoredChapterContent("invalid", 2), StoredChapterContent("future", 99))) {
            val loader = ChapterLoader(SavedChapterRepository { record })
            try {
                loader.load(key, pages) { error("Must not resolve source access") }
                fail("Invalid saved content was hidden")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun storageIoAccessAndCancellationFailuresPropagateUnchangedWithoutFallback() = runBlocking {
        for (failure in listOf(IOException("disk"), SecurityException("denied"), CancellationException("cancelled"))) {
            val loader = ChapterLoader(SavedChapterRepository { throw failure })
            try {
                loader.load(key, pages) { error("Must not resolve source access") }
                fail("Storage failure was hidden")
            } catch (actual: Exception) { assertSame(failure, actual) }
        }
    }

    @Test fun sourceResolutionAndReadFailuresPropagateUnchangedWithoutRetry() = runBlocking {
        val loader = ChapterLoader(SavedChapterRepository { null })
        for (failure in listOf(IOException("network"), SecurityException("source denied"), CancellationException("source cancelled"))) {
            for (duringResolution in listOf(true, false)) {
                var resolutions = 0
                try {
                    loader.load(key) {
                        resolutions++
                        if (duringResolution) throw failure
                        ChapterPageAccess.Available { throw failure }
                    }
                    fail("Source failure was hidden")
                } catch (actual: Exception) { assertSame(failure, actual) }
                assertEquals(1, resolutions)
            }
        }
    }

    @Test fun cancellationBeforeEntryNeverReadsStorageOrSource() = runBlocking {
        val loader = ChapterLoader(SavedChapterRepository { error("Cancelled read") })
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            loader.load(key) { error("Cancelled source resolution") }
            fail("Cancelled load returned")
        }
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test fun nonCooperativeStorageResolutionAndSourceCannotReturnLatePagesOrStartFallback() = runBlocking {
        for (stage in 0..2) {
            var resolutions = 0
            var reads = 0
            val loader = ChapterLoader(SavedChapterRepository {
                if (stage == 0) currentCoroutineContext().cancel()
                null
            })
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                loader.load(key) {
                    resolutions++
                    if (stage == 1) currentCoroutineContext().cancel()
                    ChapterPageAccess.Available {
                        reads++
                        currentCoroutineContext().cancel()
                        pages
                    }
                }
                fail("Late pages returned")
            }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(if (stage == 0) 0 else 1, resolutions)
            assertEquals(if (stage == 2) 1 else 0, reads)
        }
    }
}
