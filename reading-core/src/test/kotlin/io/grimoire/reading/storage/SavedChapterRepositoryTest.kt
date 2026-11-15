package io.grimoire.reading.storage

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.encodeChapterContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SavedChapterRepositoryTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")

    @Test fun readsExplicitLegacyAndNewFormatsUsingTheExactIdentity() = runBlocking {
        val records = listOf(StoredChapterContent("Legacy\u001c<i>Legacy</i>\u001f\u001d", 1),
            encodeChapterContent(listOf(NovelPage(0, PageContent.Text("New\u0000\u001f", "<b>New</b>")))))
        for (record in records) {
            val requests = mutableListOf<ChapterKey>()
            val repository = SavedChapterRepository { requested -> requests += requested; record }
            val pages = repository.read(key)!!
            assertEquals(listOf(key), requests)
            if (record.version == 1) {
                assertEquals(PageContent.Text("Legacy", "<i>Legacy</i>"), pages[0].content)
                assertTrue(pages[1].content is PageContent.Separator)
            } else {
                assertEquals(record, encodeChapterContent(pages))
            }
        }
    }

    @Test fun onlyAnAbsentRecordAllowsCallerFallback() = runBlocking {
        var calls = 0
        val repository = SavedChapterRepository { requested -> assertEquals(key, requested); calls++; null }
        val fallback = listOf(NovelPage(0, PageContent.Text("Fetched")))
        var fetches = 0
        assertSame(fallback, repository.read(key) ?: run { fetches++; fallback })
        assertEquals(1, calls)
        assertEquals(1, fetches)
    }

    @Test fun emptySavedContentIsPresentAndDoesNotTriggerCallerFallback() = runBlocking {
        for ((record, expectedSize) in listOf(encodeChapterContent(emptyList()) to 0,
            StoredChapterContent("", 1) to 1)) {
            val repository = SavedChapterRepository { record }
            val pages = repository.read(key) ?: error("Empty saved content was treated as absent")
            assertEquals(expectedSize, pages.size)
        }
    }

    @Test fun malformedAndFutureContentNeverBecomesAnAbsentRecord() = runBlocking {
        for (record in listOf(StoredChapterContent("prose", 99), StoredChapterContent("not json", 2),
            StoredChapterContent("[[\"unknown\"]]", 2))) {
            var fetched = false
            val repository = SavedChapterRepository { record }
            try {
                repository.read(key) ?: run { fetched = true; emptyList() }
                fail("Invalid saved content was accepted")
            } catch (_: IllegalArgumentException) { }
            assertFalse(fetched)
        }
    }

    @Test fun ioAccessAndCancellationFailuresPropagateWithoutFallback() = runBlocking {
        for (failure in listOf(IOException("disk failure"), SecurityException("denied"),
            CancellationException("cancelled"))) {
            var fetched = false
            val repository = SavedChapterRepository { throw failure }
            try {
                repository.read(key) ?: run { fetched = true; emptyList() }
                fail("Storage failure was hidden")
            } catch (actual: Exception) { assertSame(failure, actual) }
            assertFalse(fetched)
        }
    }
}
