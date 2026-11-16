package io.grimoire.app.data.local

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.NovelKey
import io.grimoire.reading.encodeChapterContent
import io.grimoire.reading.storage.LibraryEntry
import io.grimoire.reading.storage.LibraryDao
import io.grimoire.reading.storage.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStorageTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LibraryRepository
    private val key = NovelKey(17, "/book")

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java).build()
        repository = LibraryRepository(RoomLibraryDao(db))
    }

    @After fun cleanup() { db.close() }

    @Test fun membershipPreservesEveryOtherNovelFieldAndAllOfflineState() = runBlocking {
        val category = CategoryEntity(id = 7, name = "Hidden", isHidden = true)
        db.categoryDao().upsert(category)
        val novel = NovelEntity(id = 1, sourceId = key.sourceId, url = key.url, title = "Source title",
            author = "Source author", description = "Source description", favorite = false,
            categoryId = 7, chapterSortOrder = 2, lastReadAt = 123, lastAccessedAt = 456,
            customCoverPath = "/fixture/cover", customCoverUrl = "https://fixture.invalid/cover",
            userRating = 8, readerTextAlign = 2, notifyOnNewChapters = true, autoDownloadNewChapters = true,
            overrideTitle = "Reader title", overrideAuthor = "", overrideDescription = "", overrideStatus = 2,
            overrideGenres = "")
        db.novelDao().upsert(novel)
        val chapter = ChapterEntity(id = 1, novelId = 1, url = "/chapter", name = "Chapter", read = true,
            readProgress = 0.625f, readAnchorItemIndex = 3, readAnchorItemOffset = 17, wordCount = 2, downloadStatus = 3)
        db.chapterDao().insertAll(listOf(chapter))
        val body = encodeChapterContent(listOf(NovelPage(0, PageContent.Text("Offline prose"))))
        db.chapterDao().setDownloadedContent(1, body, 3)
        assertEquals(LibraryEntry(key, "Reader title", false, "", ""), repository.find(key))
        for (value in listOf(true, true, false, false)) {
            assertEquals(LibraryEntry(key, "Reader title", value, "", ""), repository.setMembership(key, value))
            assertEquals(novel.copy(favorite = value), db.novelDao().getById(1))
            assertEquals(chapter, db.chapterDao().getByUrl(1, chapter.url))
            assertEquals(body, db.chapterDao().getContent(1))
        }
        assertEquals(35, db.openHelper.readableDatabase.version)
        assertEquals(listOf(category), db.categoryDao().getAllOnce())
    }

    @Test fun missingKeysDoNotCreateBooksAndIdentityUsesBothSourceAndNovelUrl() = runBlocking {
        val keys = listOf(key, NovelKey(0, key.url), NovelKey(key.sourceId, "/other"))
        for ((index, identity) in keys.withIndex()) {
            db.novelDao().upsert(NovelEntity(sourceId = identity.sourceId, url = identity.url, title = "Book $index"))
        }
        assertTrue(repository.setMembership(key, true)!!.inLibrary)
        for (identity in keys.drop(1)) assertFalse(repository.find(identity)!!.inLibrary)
        for (missing in listOf(NovelKey(18, key.url), key.copy(url = "/missing"))) {
            assertNull(repository.find(missing))
            assertNull(repository.setMembership(missing, true))
        }
        assertEquals(3, db.novelDao().getAll().size)
        assertEquals("Book 0", repository.find(key)!!.title)
    }

    @Test fun sqliteFailureAndCancellationBeforeCommitPreserveMembership() = runBlocking {
        val novel = NovelEntity(id = 1, sourceId = key.sourceId, url = key.url, title = "Book")
        db.novelDao().upsert(novel)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_membership BEFORE UPDATE OF favorite ON novels
            BEGIN SELECT RAISE(ABORT, 'fixture membership failure'); END""")
        try { repository.setMembership(key, true); fail("SQLite failure hidden") }
        catch (failure: SQLiteException) { assertTrue(failure.message.orEmpty().contains("fixture membership failure")) }
        assertEquals(novel, db.novelDao().getById(1))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_membership")
        val cancelled = Job().apply { cancel() }
        try { withContext(cancelled) { repository.setMembership(key, true) }; fail("Cancellation hidden") }
        catch (_: CancellationException) { }
        assertEquals(novel, db.novelDao().getById(1))

        // Execute the real Room write, but hold its surrounding transaction
        // before commit. Cancellation must roll this unacknowledged write back.
        val written = CompletableDeferred<Unit>()
        val room = RoomLibraryDao(db)
        val inFlight = LibraryRepository(object : LibraryDao {
            override suspend fun find(key: NovelKey) = room.find(key)
            override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? = db.withTransaction {
                checkNotNull(room.setMembership(key, inLibrary))
                written.complete(Unit)
                awaitCancellation()
            }
        })
        val command = launch(Dispatchers.IO) { inFlight.setMembership(key, true) }
        withTimeout(5_000) { written.await() }
        command.cancelAndJoin()
        assertTrue(command.isCancelled)
        assertEquals(novel, db.novelDao().getById(1))
        assertTrue(repository.setMembership(key, true)!!.inLibrary)
    }
}
