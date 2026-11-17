package io.grimoire.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.*
import io.grimoire.app.data.local.entity.*
import io.grimoire.reading.*
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.encodeChapterContent
import io.grimoire.reading.anchor.*
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReadingStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "reading-state-${UUID.randomUUID()}.db"
    private var opened: AppDatabase? = null
    private val db get() = requireNotNull(opened)
    private lateinit var saved: RoomSavedChapterDao
    private lateinit var dao: RoomReadingStateDao
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val metadata = ChapterEntity(id = 1, novelId = 1, url = key.url, name = "Chapter", readProgress = 0.625f,
        readAnchorItemIndex = 3, readAnchorItemOffset = 17, wordCount = 7)
    private val pages = listOf(NovelPage(0, PageContent.Text("Original")))
    private val document = CanonicalDocument.fromPages(key, pages)
    private val legacy = StoredChapterContent("Original\u001c<b>Original</b>", 1)
    @After fun cleanup() { opened?.close(); context.deleteDatabase(name) }
    private fun open(migration: Migration = MIGRATION_35_36) {
        opened = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(migration).addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        saved = RoomSavedChapterDao(db); dao = RoomReadingStateDao(db, saved)
    }
    private suspend fun seed() {
        open(); db.novelDao().upsert(NovelEntity(id = 1, sourceId = 17, url = key.novel.url, title = "Book", favorite = true))
        db.chapterDao().insertAll(listOf(metadata)); db.chapterDao().upsertContent(ChapterContentEntity(1, legacy))
    }
    private suspend fun prepare() = requireNotNull(dao.prepare(key))
    private fun explicit(p: PreparedReadingState, read: Boolean, id: String = UUID.randomUUID().toString()) = ReadingStateCommand(id, p.binding,
        if (read) ReadingCompletionInput.MarkRead(key, 100) else ReadingCompletionInput.MarkUnread(key), null, 0)
    private suspend fun session(kind: ReadingStateWriterKind): ReadingStateSession = requireNotNull(dao.register(prepare().binding,
        requireNotNull(saved.readObserved(key).observation), document, kind))
    private fun automatic(s: ReadingStateSession, sequence: Long = 1, id: String = UUID.randomUUID().toString()): ReadingStateCommand {
        val input = if (s.kind == ReadingStateWriterKind.VISUAL) {
            val p = ReadingProjection.create(key, pages, listOf(ProjectedPage(0, pages[0])))
            ReadingCompletionInput.Visual(key, VisualCompletionEvidence(s.id, s.content, document.logicalRevision, AndroidViewportV1.create(0, 0, 1, 1f)!!),
                VisualCompletionContext(s.id, s.content, p, true, true), AutomaticReadPolicy.AtEnd, 100)
        } else ReadingCompletionInput.Audio(key, AudioCompletionEvidence(s.id, s.content, document.logicalRevision, 1, 0, true),
            AudioCompletionContext(s.id, s.content, document, 1, true, true), 100)
        return ReadingStateCommand(id, s.binding, input, s, sequence)
    }
    private fun rejected(result: ReadingStateCommit, why: ReadingStateRejection) = assertEquals(ReadingStateCommit.NotApplied(why), result)

    @Test fun explicitReplayAndSameValueIntentsPreserveEveryUnrelatedLegacyField() = runBlocking<Unit> {
        seed(); val novel = db.novelDao().getById(1); val content = db.chapterDao().getContent(1)
        val p = prepare(); val command = explicit(p, true)
        assertEquals(ReadStatus.UNREAD, p.value.status)
        val applied = dao.commit(command) as ReadingStateCommit.Committed; assertFalse(applied.replayed)
        assertEquals(metadata.copy(read = true, firstReadAt = 100), db.chapterDao().getByUrl(1, key.url))
        val same = dao.commit(explicit(prepare(), true)) as ReadingStateCommit.Committed; assertEquals(2L, same.intentRevision)
        dao.commit(explicit(prepare(), false))
        val replay = dao.commit(command) as ReadingStateCommit.Committed
        assertTrue(replay.replayed); assertEquals(ReadStatus.READ, replay.valueAtCommit.status); assertEquals(ReadStatus.UNREAD, replay.current.status)
        assertEquals(100L, replay.current.firstReadAt); assertEquals(3L, replay.intentRevision)
        rejected(dao.commit(command.copy(input = ReadingCompletionInput.MarkRead(key, 101))), ReadingStateRejection.CONFLICT)
        assertEquals(metadata.copy(firstReadAt = 100), db.chapterDao().getByUrl(1, key.url))
        assertEquals(novel, db.novelDao().getById(1)); assertEquals(content, db.chapterDao().getContent(1))
        val other = key.copy(url = "/second")
        db.chapterDao().insertAll(listOf(metadata.copy(id = 2, url = other.url)))
        val second = dao.prepare(other)!!
        dao.commit(ReadingStateCommand("other", second.binding, ReadingCompletionInput.MarkRead(other, 200), null, 0))
        assertEquals(200L, dao.read(other)!!.firstReadAt); assertEquals(ReadStatus.UNREAD, dao.read(key)!!.status)
        assertEquals(100L, dao.read(key)!!.firstReadAt)
    }

    @Test fun queuedVisualAndAudioCannotUndoUnreadButFreshRebindCan() = runBlocking<Unit> {
        seed()
        for (kind in ReadingStateWriterKind.entries) {
            dao.commit(explicit(prepare(), false)); val s = session(kind); val command = automatic(s)
            assertTrue(dao.commit(command) is ReadingStateCommit.Committed)
            dao.commit(explicit(prepare(), false))
            rejected(dao.commit(automatic(s, 2)), ReadingStateRejection.STALE)
            val replay = dao.commit(command) as ReadingStateCommit.Committed; assertEquals(ReadStatus.UNREAD, replay.current.status)
            val fresh = session(kind); assertTrue(dao.commit(automatic(fresh)) is ReadingStateCommit.Committed)
            fun receipts() = db.openHelper.readableDatabase.query("SELECT count(*) FROM reading_state_receipts").use { it.moveToFirst(); it.getLong(0) }
            val count = receipts()
            repeat(10) { rejected(dao.commit(automatic(fresh, 2)), ReadingStateRejection.NO_CHANGE) }
            assertEquals(count, receipts())
        }
        assertEquals(100L, dao.read(key)!!.firstReadAt)
    }

    @Test fun legacySameValueWritesIdentityAbaAndRecreationRetireOldBindings() = runBlocking<Unit> {
        seed(); val sql = db.openHelper.writableDatabase
        db.novelDao().upsert(NovelEntity(id = 2, sourceId = 18, url = "/second-book", title = "Second"))
        val mutations: List<suspend () -> Unit> = listOf(
            { db.chapterDao().setReadRaw(1, false) },
            { sql.execSQL("UPDATE chapters SET url='/temporary' WHERE id=1"); sql.execSQL("UPDATE chapters SET url='/chapter' WHERE id=1") },
            { sql.execSQL("UPDATE chapters SET novelId=2 WHERE id=1"); sql.execSQL("UPDATE chapters SET novelId=1 WHERE id=1") },
            { sql.execSQL("UPDATE novels SET url='/temporary-book' WHERE id=1"); sql.execSQL("UPDATE novels SET url='/book' WHERE id=1") },
            { sql.execSQL("UPDATE novels SET sourceId=18 WHERE id=1"); sql.execSQL("UPDATE novels SET sourceId=17 WHERE id=1") },
            { sql.execSQL("DELETE FROM chapters WHERE id=1"); db.chapterDao().insertAll(listOf(metadata)) },
        )
        for (mutate in mutations) {
            val p = prepare(); val command = explicit(p, false); dao.commit(command)
            mutate(); rejected(dao.commit(command), ReadingStateRejection.STALE)
            val fresh = prepare(); assertNotEquals(p.binding.incarnation, fresh.binding.incarnation)
            assertTrue(dao.commit(explicit(fresh, false)) is ReadingStateCommit.Committed)
        }
        assertNull(dao.prepare(key.copy(url = "/missing")))
    }

    @Test fun contentReplacementAndRelockRevokeAutomaticAuthorityButAllowExplicitState() = runBlocking<Unit> {
        seed(); val s = session(ReadingStateWriterKind.AUDIO)
        db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(listOf(NovelPage(0, PageContent.Text("Different"))))))
        rejected(dao.commit(automatic(s)), ReadingStateRejection.STALE)
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET locked=1 WHERE id=1")
        assertNull(dao.register(prepare().binding, s.content, document, s.kind))
        db.chapterDao().setWordCount(1, 0)
        assertTrue(dao.commit(explicit(prepare(), true)) is ReadingStateCommit.Committed)
        assertEquals(0, db.chapterDao().getByUrl(1, key.url)!!.wordCount)
        db.chapterDao().backfillWordCountsFromDownloads()
        assertEquals(1, db.chapterDao().getByUrl(1, key.url)!!.wordCount)
        assertTrue(db.chapterDao().getByUrl(1, key.url)!!.locked)
    }

    @Test fun realRollbackCancellationAndLostReplyKeepValueProtocolAndReceiptAtomic() = runBlocking<Unit> {
        seed(); val p = prepare(); val command = explicit(p, true); val storage = db.readingStateStorageDao()
        val before = storage.find(1)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER state_fault BEFORE INSERT ON reading_state_receipts BEGIN SELECT RAISE(ABORT,'state fault'); END")
        try { dao.commit(command); fail("Expected write fault") } catch (e: Exception) { assertTrue(e.message!!.contains("state fault")) }
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url)); assertEquals(before, storage.find(1)); assertNull(storage.receipt(1, command.id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER state_fault")
        val entered = CompletableDeferred<Unit>()
        val child = launch(Dispatchers.Default) { db.withTransaction { dao.commit(command); entered.complete(Unit); awaitCancellation() } }
        entered.await(); child.cancelAndJoin()
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url)); assertEquals(before, storage.find(1)); assertNull(storage.receipt(1, command.id))
        val lost = object : ReadingStateDao by dao { override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit { dao.commit(command); throw IOException("Lost reply") } }
        try { lost.commit(command); fail("Lost reply expected") } catch (e: IOException) { assertEquals("Lost reply", e.message) }
        assertTrue((dao.commit(command) as ReadingStateCommit.Committed).replayed)
        assertEquals(100L, dao.read(key)!!.firstReadAt)
    }

    @Test fun twoStoreOwnersAndReopenKeepReceiptsButRejectUncommittedOldScope() = runBlocking<Unit> {
        seed(); val first = prepare(); val command = explicit(first, true)
        val other = Room.databaseBuilder(context, AppDatabase::class.java, name).addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        try {
            val second = RoomReadingStateDao(other, RoomSavedChapterDao(other)); val competing = second.prepare(key)!!
            dao.commit(command); rejected(second.commit(explicit(competing, false)), ReadingStateRejection.CONFLICT)
            assertTrue(second.commit(explicit(second.prepare(key)!!, false)) is ReadingStateCommit.Committed)
        } finally { other.close() }
        db.close(); open()
        assertEquals(ReadStatus.UNREAD, dao.read(key)!!.status)
        assertTrue((dao.commit(command) as ReadingStateCommit.Committed).replayed)
        rejected(dao.commit(explicit(first, false)), ReadingStateRejection.STALE)
        assertTrue(dao.commit(explicit(prepare(), true)) is ReadingStateCommit.Committed)
    }

    @Test fun unknownProtocolFieldsFailClosedWithoutOverwrite() = runBlocking<Unit> {
        seed(); prepare(); val storage = db.readingStateStorageDao(); val original = storage.find(1)!!
        val corrupt = original.copy(state = original.state.replace("\"format\":1", "\"format\":99")); storage.put(corrupt)
        try { dao.prepare(key); fail("Corruption must not become absence") } catch (_: IllegalArgumentException) { }
        assertEquals(corrupt, storage.find(1)); assertEquals(metadata, db.chapterDao().getByUrl(1, key.url))
    }

    @Test fun frozenDb35MigrationRollsBackAndPreservesOldSchemaAndEveryLegacyValue() = runBlocking<Unit> {
        context.getDatabasePath(name).parentFile!!.mkdirs()
        val oldCheckpoint = encodeReadingCheckpoint(ReadingCheckpointState(ReadingSessionBinding("old", ChapterStorageObservation(key, "old", "old", ChapterContentPresence.SAVED), document.logicalRevision), 1,
            ReadingAnchor(key, document.logicalRevision, AnchorPosition.End)))
        val oldDefinitions: List<String>
        lateinit var oldRows: Map<String, List<List<Pair<Int, String?>>>>
        fun snapshot(query: (String) -> android.database.Cursor): Map<String, List<List<Pair<Int, String?>>>> {
            val names = query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata','reading_states','reading_state_receipts') ORDER BY name").use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
            return names.associateWith { name -> query("SELECT * FROM \"$name\" ORDER BY rowid").use { c -> buildList { while(c.moveToNext()) add((0 until c.columnCount).map { c.getType(it) to c.getString(it) }) } } }
        }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { raw ->
            val schema = instrumentation.context.assets.open("chapter-codec/v35.sql").bufferedReader().use { it.readText() }
            schema.lineSequence().filter { it.isNotBlank() && !it.startsWith("--") }.forEach(raw::execSQL)
            raw.execSQL("INSERT INTO novels(id,sourceId,url,title,genres,status,favorite,lastUpdated,chapterSortOrder,lastReadAt,lastAccessedAt,readerTextAlign,notifyOnNewChapters,notifyOnNewLockedChapters,autoDownloadNewChapters) VALUES(1,17,'/book','Book','',0,1,0,0,0,0,0,0,0,0)")
            raw.execSQL("INSERT INTO chapters(id,novelId,url,name,uploadDate,chapterNumber,read,readProgress,readAnchorItemIndex,readAnchorItemOffset,downloadStatus,queueOrder,firstReadAt,wordCount,locked) VALUES(1,1,'/chapter','Chapter',0,-1,1,0.625,3,17,0,0,789,7,0)")
            raw.execSQL("INSERT INTO chapter_content VALUES(1,?,1)", arrayOf(legacy.content))
            raw.execSQL("INSERT INTO chapter_download_state(chapterId,revision,committedAttemptId,cacheRevision) VALUES(1,'generation','receipt','cache')")
            raw.execSQL("INSERT INTO reading_checkpoints VALUES(1,?)", arrayOf(oldCheckpoint))
            raw.execSQL("INSERT INTO categories(id,name,`order`,isDefault,isHidden) VALUES(3,'Fixture',0,0,1)")
            raw.execSQL("UPDATE novels SET categoryId=3 WHERE id=1")
            raw.execSQL("INSERT INTO reading_history(id,sourcePackage,novelId,novelUrl,novelTitle,chapterUrl,chapterName,chapterNumber,openedAt) VALUES(1,'fixture',1,'/book','Book','/chapter','Chapter',1,17)")
            raw.execSQL("INSERT INTO browsing_history(id,sourcePackage,novelId,novelUrl,novelTitle,openedAt) VALUES(1,'fixture',1,'/book','Book',19)")
            oldRows = snapshot { raw.rawQuery(it, null) }
            oldDefinitions = raw.rawQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        }
        open(object : Migration(35, 36) { override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_35_36.migrate(db); error("Migration fault") } })
        try { db.openHelper.writableDatabase; fail("Migration must roll back") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Migration fault")) }
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(35, raw.version)
            raw.rawQuery("SELECT count(*) FROM sqlite_master WHERE name='reading_states'", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
        open(); assertEquals(36, db.openHelper.writableDatabase.version)
        assertEquals(metadata.copy(read = true, firstReadAt = 789), db.chapterDao().getByUrl(1, key.url)); assertEquals(legacy, db.chapterDao().getContent(1))
        assertEquals(oldCheckpoint, db.readingCheckpointStateDao().find(1)!!.state)
        assertEquals("receipt", db.chapterDownloadStateDao().find(1)!!.committedAttemptId)
        assertEquals("cache", db.chapterDownloadStateDao().find(1)!!.cacheRevision)
        assertNull(db.readingStateStorageDao().find(1)); dao.read(key); assertNull(db.readingStateStorageDao().find(1))
        val after = db.openHelper.readableDatabase.query("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT IN ('reading_states','reading_state_receipts','reading_state_legacy','reading_state_chapter_identity','reading_state_novel_identity') ORDER BY name").use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        assertEquals(oldDefinitions, after); assertEquals(oldRows, snapshot { db.openHelper.readableDatabase.query(it) }); prepare()
        assertEquals(oldRows, snapshot { db.openHelper.readableDatabase.query(it) })
        assertEquals(metadata.copy(read = true, firstReadAt = 789), db.chapterDao().getByUrl(1, key.url))
    }
}
