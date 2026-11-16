package io.grimoire.app.data.local

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.local.entity.ChapterContentEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.*
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChapterObservationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "chapter-observation-${UUID.randomUUID()}.db"
    private var database: AppDatabase? = null
    private val db get() = requireNotNull(database)
    private lateinit var observer: RoomSavedChapterDao
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val metadata = ChapterEntity(id = 1, novelId = 1, url = key.url, name = "Chapter", read = true,
        readProgress = 0.625f, readAnchorItemIndex = 3, readAnchorItemOffset = 17, firstReadAt = 789, wordCount = 7)
    private val pages = listOf(NovelPage(0, PageContent.Text("Original")))
    private val legacy = StoredChapterContent("Original\u001c<b>Original</b>\u001f\u001ehttps://example.org/image", 1)

    private fun open() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        observer = RoomSavedChapterDao(db)
    }

    private suspend fun seed(content: StoredChapterContent? = null) {
        open()
        db.novelDao().upsert(NovelEntity(id = 1, sourceId = 17, url = key.novel.url, title = "Book"))
        db.chapterDao().insertAll(listOf(metadata))
        content?.let { db.chapterDao().upsertContent(ChapterContentEntity(1, it)) }
    }

    @After fun cleanup() { database?.close(); context.deleteDatabase(name) }

    @Test fun savedLegacyObservationPreservesBytesCacheIdentityAndEveryLegacyField() = runBlocking {
        seed(legacy)
        val chapters = db.chapterDao()
        val novel = db.novelDao().getById(1)
        val original = observer.readSnapshot(key)
        assertNull(db.chapterDownloadStateDao().find(1)) // Old read API remains read-only.
        val read = observer.readObserved(key)
        val observation = requireNotNull(read.observation)
        assertEquals(original, read.saved)
        assertEquals(legacy, chapters.getContent(1))
        assertEquals(ChapterContentPresence.SAVED, observation.presence)
        assertNull(read.saved!!.cacheRevision)
        assertNull(db.chapterDownloadStateDao().find(1)!!.cacheRevision)
        assertNull(db.chapterDownloadStateDao().find(1)!!.committedAttemptId)
        assertTrue(observer.isObservationCurrent(observation))
        assertEquals(read, observer.readObserved(key))
        assertEquals(metadata, chapters.getByUrl(1, key.url))
        assertEquals(novel, db.novelDao().getById(1))
        assertEquals(34, db.openHelper.readableDatabase.version)
    }

    @Test fun explicitAbsenceIsDifferentFromEmptySavedMissingLockedAndForeignSubjects() = runBlocking {
        seed()
        val absent = requireNotNull(observer.readObserved(key).observation)
        assertEquals(ChapterContentPresence.ABSENT, absent.presence)
        assertTrue(observer.isObservationCurrent(absent))
        assertNull(observer.readObserved(key.copy(url = "/missing")).observation)
        assertNull(observer.readObserved(key.copy(novel = key.novel.copy(sourceId = 18))).observation)
        db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(emptyList())))
        assertFalse(observer.isObservationCurrent(absent))
        val empty = observer.readObserved(key)
        assertEquals(ChapterContentPresence.SAVED, empty.observation!!.presence)
        assertTrue(decodeChapterContent(empty.saved!!.content).isEmpty())
        db.chapterDao().updateChapterMeta(1, key.url, "Chapter", 0, -1f, null, true)
        assertFalse(observer.isObservationCurrent(empty.observation!!))
        val locked = observer.readObserved(key)
        assertNotNull(locked.saved) // Existing Room selection is unchanged; no write authority is inferred.
        assertNull(locked.observation)
    }

    @Test fun contentAbaRestoreRelockAndSameRowIdRecreationNeverReviveAnOldGeneration() = runBlocking {
        seed(encodeChapterContent(pages))
        val chapters = db.chapterDao()
        suspend fun current() = requireNotNull(observer.readObserved(key).observation)
        var before = current()
        chapters.upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
        assertFalse(observer.isObservationCurrent(before))
        before = current(); chapters.deleteContent(1)
        assertFalse(observer.isObservationCurrent(before))
        val absent = current(); chapters.upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
        assertFalse(observer.isObservationCurrent(absent))
        before = current(); chapters.restoreChapters(listOf(requireNotNull(chapters.getByUrl(1, key.url))))
        assertFalse(observer.isObservationCurrent(before))
        before = current(); chapters.updateChapterMeta(1, key.url, "Chapter", 0, -1f, null, true)
        chapters.updateChapterMeta(1, key.url, "Chapter", 0, -1f, null, false)
        assertFalse(observer.isObservationCurrent(before))
        before = current()
        for (column in listOf("url", "sourceId")) {
            val value: Any = if (column == "url") "/other" else 18
            db.openHelper.writableDatabase.execSQL("UPDATE novels SET $column=? WHERE id=1", arrayOf(value))
            assertFalse(observer.isObservationCurrent(before))
            db.openHelper.writableDatabase.execSQL("UPDATE novels SET $column=? WHERE id=1", arrayOf(if (column == "url") key.novel.url else 17))
            assertFalse(observer.isObservationCurrent(before))
            before = current()
        }
        chapters.deleteByIds(listOf(1)); chapters.insertAll(listOf(metadata))
        chapters.upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
        assertFalse(observer.isObservationCurrent(before))
        assertNull(db.chapterDownloadStateDao().find(1)) // Validation must not reseed a recreated subject.
        assertNotEquals(before.generation, current().generation)
    }

    @Test fun observingDuringADownloadPreservesItsAttemptReceiptAndCacheGeneration() = runBlocking {
        seed()
        val file = File(context.cacheDir, "observation-owner-${UUID.randomUUID()}.lock")
        val owner = DownloadWriteOwner(file)
        try {
            val downloads = DownloadRepository(RoomDownloadDao(db, owner))
            db.chapterDao().setDownloadStatus(1, 1)
            val first = requireNotNull(downloads.claim(key))
            val during = requireNotNull(observer.readObserved(key).observation)
            val active = db.chapterDownloadStateDao().find(1)
            assertEquals(first.id, active!!.activeAttemptId)
            assertTrue(observer.isObservationCurrent(during))
            assertTrue(downloads.commit(first, pages) is DownloadCommit.Committed)
            assertFalse(observer.isObservationCurrent(during))
            val receipt = downloads.currentReceipt(key)
            val saved = observer.readObserved(key)
            val state = db.chapterDownloadStateDao().find(1)
            db.chapterDao().setDownloadStatus(1, 5)
            val refresh = requireNotNull(downloads.claim(key))
            assertEquals(saved, observer.readObserved(key))
            assertTrue(observer.isObservationCurrent(saved.observation!!))
            assertEquals(receipt, downloads.currentReceipt(key))
            assertEquals(state!!.cacheRevision, observer.readObserved(key).saved!!.cacheRevision)
            assertTrue(downloads.commit(refresh, pages) is DownloadCommit.Committed)
            assertFalse(observer.isObservationCurrent(saved.observation!!))
        } finally { owner.close(); file.delete() }
    }

    @Test fun sourceLoadKeepsTheOriginalAbsenceAndReopenRejectsItsOldObserverScope() = runBlocking {
        seed()
        val repository = SavedChapterRepository(observer)
        val loader = ChapterLoader(repository)
        val result = loader.load(key) {
            ChapterPageAccess.Available {
                db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
                pages
            }
        }
        val observation = requireNotNull(result.storageObservation)
        assertEquals(ChapterOrigin.SOURCE, result.origin)
        assertEquals(ChapterContentPresence.ABSENT, observation.presence)
        assertFalse(repository.isObservationCurrent(observation))
        val saved = loader.load(key) { error("Saved body wins") }
        assertEquals(CanonicalDocument.fromPages(key, result.pages).logicalRevision,
            CanonicalDocument.fromPages(key, saved.pages).logicalRevision)
        val old = requireNotNull(saved.storageObservation)
        val oldObserver = observer
        db.close(); open()
        assertFalse(oldObserver.isObservationCurrent(old))
        assertFalse(observer.isObservationCurrent(old))
        val next = requireNotNull(observer.readObserved(key).observation)
        assertEquals(old.generation, next.generation)
        assertNotEquals(old.scope, next.scope)
        assertTrue(observer.isObservationCurrent(next))
    }

    @Test fun protocolIdentityAllocationFailureRollsBackAndCannotFallBackToSource() = runBlocking {
        seed(legacy)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER observation_fault BEFORE INSERT ON chapter_download_state " +
            "BEGIN SELECT RAISE(ABORT,'allocation failed'); END")
        try {
            ChapterLoader(SavedChapterRepository(observer)).load(key, pages) { error("Must not hide storage failure") }
            fail("Allocation succeeded")
        } catch (_: SQLiteException) { }
        assertNull(db.chapterDownloadStateDao().find(1))
        assertEquals(legacy, observer.read(key))
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER observation_fault")
        assertTrue(observer.isObservationCurrent(requireNotNull(observer.readObserved(key).observation)))
    }
}
