package io.grimoire.app.ui.screen.reader

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.*
import io.grimoire.app.data.local.*
import io.grimoire.app.data.local.entity.ChapterContentEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderCheckpointAdoptionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "reader-adoption-${UUID.randomUUID()}.db"
    private var database: AppDatabase? = null
    private val db get() = requireNotNull(database)
    private lateinit var saved: RoomSavedChapterDao
    private lateinit var dao: RoomReadingCheckpointDao
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val pages = listOf(NovelPage(0, PageContent.Text("")), NovelPage(1, PageContent.Text("   ")),
        NovelPage(2, PageContent.Text("First")), NovelPage(3, PageContent.Image("remote")),
        NovelPage(4, PageContent.Separator()), NovelPage(5, PageContent.Text("Last")))
    private val metadata = ChapterEntity(id = 1, novelId = 1, url = key.url, name = "Chapter", read = true,
        readProgress = 0.625f, readAnchorItemIndex = 3, readAnchorItemOffset = 17, firstReadAt = 789, wordCount = 7)

    private fun open() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        saved = RoomSavedChapterDao(db); dao = RoomReadingCheckpointDao(db, saved)
    }
    private suspend fun seed(content: Boolean = true) {
        open(); db.novelDao().upsert(NovelEntity(id = 1, sourceId = 17, url = key.novel.url, title = "Book", favorite = true))
        db.chapterDao().insertAll(listOf(metadata))
        if (content) db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
    }
    private suspend fun frame(coordinator: ReaderCheckpoints, legacy: LegacyReaderViewport? = null): ReaderContent {
        val loaded = ChapterLoader(SavedChapterRepository(saved)).load(key) { error("No source access") }
        return prepareReaderCheckpoint(androidReaderContent(1, key, loaded) { "file:///cached" }, coordinator, legacy)
    }
    @After fun cleanup() { database?.close(); context.deleteDatabase(name) }

    @Test fun productionPreparationCapturesCanonicalBlockAndRestoresAfterRoomReopenWithoutLayoutWrites() = runBlocking<Unit> {
        seed(); val coordinator = ReaderCheckpoints(dao)
        val before = db.chapterDao().getContent(1); val novel = db.novelDao().getById(1)
        val content = frame(coordinator, LegacyReaderViewport(3, 17)); val load = content.checkpoint!!
        assertEquals(CheckpointNotice.LEGACY, load.restore.notice); assertEquals(ReaderViewport(3, 17), load.restore.viewport)
        assertNull(coordinator.capture(load, 1)); coordinator.flush(); assertNull(dao.read(key))
        coordinator.userPosition(load); val anchor = coordinator.capture(load, 1)!!; coordinator.flush()
        assertEquals(2, (anchor.position as AnchorPosition.Block).ordinal)
        assertEquals(PageContent.Image("file:///cached"), content.projection.body[1].page.content)
        assertEquals(AnchorBlock.Image("remote"), content.projection.document!!.blocks[3])
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url)); assertEquals(novel, db.novelDao().getById(1))
        assertEquals(before, db.chapterDao().getContent(1))
        coordinator.retire(); db.close(); open()
        val reopened = ReaderCheckpoints(dao); val fresh = frame(reopened)
        assertTrue(fresh.checkpoint!!.restore.logical is AnchorResolution.Exact)
        assertEquals(ReaderViewport(1), fresh.checkpoint!!.restore.viewport)
        assertNull(reopened.capture(fresh.checkpoint!!, 0)); reopened.flush()
        assertEquals(anchor, dao.read(key)); assertEquals(metadata, db.chapterDao().getByUrl(1, key.url))
    }

    @Test fun changedAndUnsupportedFallbacksRetainEvidenceUntilExplicitPositionSelection() = runBlocking<Unit> {
        seed(); val coordinator = ReaderCheckpoints(dao); val first = frame(coordinator)
        coordinator.userPosition(first.checkpoint!!); val old = coordinator.capture(first.checkpoint!!, 1)!!; coordinator.flush()
        db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(listOf(NovelPage(0, PageContent.Text("Replacement"))))))
        val changed = frame(coordinator); val load = changed.checkpoint!!
        assertEquals(CheckpointNotice.CHANGED, load.restore.notice); assertFalse(coordinator.canRecordPosition(load))
        assertNull(coordinator.capture(load, 1)); coordinator.flush(); assertEquals(old, dao.read(key))
        val stored = db.readingCheckpointStateDao().find(1)!!
        val state = decodeReadingCheckpoint(stored.state)
        db.readingCheckpointStateDao().put(stored.copy(state = encodeReadingCheckpoint(state.copy(anchor = old.copy(modelVersion = 99)))))
        val unsupported = frame(coordinator); val current = unsupported.checkpoint!!
        assertEquals(CheckpointNotice.UNSUPPORTED, current.restore.notice); assertFalse(coordinator.canRecordPosition(current))
        coordinator.flush(); assertEquals(99, dao.read(key)!!.modelVersion)
        coordinator.userPosition(current); coordinator.capture(current, 1); coordinator.flush()
        assertEquals(READING_ANCHOR_VERSION, dao.read(key)!!.modelVersion)
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url))
    }

    @Test fun realRoomFailureRetainsNewestCaptureAndLostReplyReplaysWithoutTouchingLegacyFields() = runBlocking<Unit> {
        seed(); var loseReply = false
        val wrapped = object : ReadingCheckpointDao by dao {
            override suspend fun commit(command: CheckpointCommand): CheckpointCommit {
                val result = dao.commit(command)
                if (loseReply) throw IOException("Lost reply")
                return result
            }
        }
        val coordinator = ReaderCheckpoints(wrapped); val content = frame(coordinator); val load = content.checkpoint!!
        coordinator.userPosition(load); coordinator.capture(load, 1)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_checkpoint BEFORE UPDATE ON reading_checkpoints BEGIN SELECT RAISE(ABORT,'fixture write fault'); END")
        try { coordinator.flush(); fail("Write fault ignored") } catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(dao.read(key)); coordinator.capture(load, 4)
        sql.execSQL("DROP TRIGGER fail_checkpoint"); loseReply = true
        try { coordinator.flush(); fail("Lost reply ignored") } catch (e: IOException) { assertEquals("Lost reply", e.message) }
        val committed = dao.read(key)!!; assertEquals(5, (committed.position as AnchorPosition.Block).ordinal)
        loseReply = false; coordinator.flush()
        assertEquals(committed, dao.read(key)); assertEquals(2L, decodeReadingCheckpoint(db.readingCheckpointStateDao().find(1)!!.state).sequence)
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url))
    }

    @Test fun staleSourceSnapshotCannotAcquireAuthorityAndRetiredLoadsCannotOverwriteFreshSavedPosition() = runBlocking<Unit> {
        seed(false)
        val result = ChapterLoader(SavedChapterRepository(saved)).load(key) { ChapterPageAccess.Available { pages } }
        db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
        val coordinator = ReaderCheckpoints(dao)
        val stale = prepareReaderCheckpoint(androidReaderContent(1, key, result), coordinator, null)
        assertEquals(CheckpointNotice.RELOAD, stale.checkpoint!!.restore.notice)
        coordinator.userPosition(stale.checkpoint!!); assertFalse(coordinator.canRecordPosition(stale.checkpoint!!))
        assertNull(coordinator.capture(stale.checkpoint!!, 1)); coordinator.flush(); assertNull(dao.read(key))
        val fresh = frame(coordinator); coordinator.userPosition(fresh.checkpoint!!); coordinator.capture(fresh.checkpoint!!, 4)
        coordinator.flush(); val anchor = dao.read(key)
        assertNull(coordinator.capture(stale.checkpoint!!, 0)); coordinator.flush()
        assertEquals(anchor, dao.read(key)); assertEquals(metadata, db.chapterDao().getByUrl(1, key.url))
    }
}
