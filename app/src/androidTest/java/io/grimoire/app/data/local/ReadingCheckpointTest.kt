package io.grimoire.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.ChapterContentEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReadingCheckpointTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "reading-checkpoint-${UUID.randomUUID()}.db"
    private var database: AppDatabase? = null
    private val db get() = requireNotNull(database)
    private lateinit var saved: RoomSavedChapterDao
    private lateinit var dao: RoomReadingCheckpointDao
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val metadata = ChapterEntity(id = 1, novelId = 1, url = key.url, name = "Chapter", read = true,
        readProgress = 0.625f, readAnchorItemIndex = 3, readAnchorItemOffset = 17, firstReadAt = 789, wordCount = 7)
    private val pages = listOf(NovelPage(0, PageContent.Text("Original")))
    private val document = CanonicalDocument.fromPages(key, pages)
    private val legacy = StoredChapterContent("Original\u001c<b>Original</b>", 1)

    @After fun cleanup() { database?.close(); context.deleteDatabase(name) }
    private fun open(migration: Migration = MIGRATION_34_35) {
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(migration).addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        saved = RoomSavedChapterDao(db); dao = RoomReadingCheckpointDao(db, saved)
    }
    private suspend fun seed(content: Boolean = false) {
        open()
        db.novelDao().upsert(NovelEntity(id = 1, sourceId = 17, url = key.novel.url, title = "Book", favorite = true))
        db.chapterDao().insertAll(listOf(metadata))
        if (content) db.chapterDao().upsertContent(ChapterContentEntity(1, legacy))
    }
    private suspend fun session() = requireNotNull(dao.register(requireNotNull(saved.readObserved(key).observation), document))
    private fun command(s: ReadingSession, sequence: Long = 1, position: AnchorPosition = AnchorPosition.Start) =
        CheckpointCommand(s, sequence, ReadingAnchor(s.document.key, s.document.logicalRevision, position))

    @Test fun orderingReplayConflictAndBackwardReadingLeaveEveryLegacyFieldAndByteUntouched() = runBlocking<Unit> {
        seed(true)
        val novel = db.novelDao().getById(1)
        val s = session(); val protocol = db.chapterDownloadStateDao().find(1)
        val end = command(s, 8, AnchorPosition.End); val backward = command(s, 9)
        assertEquals(CheckpointCommit.Committed(end.anchor, 8, false), dao.commit(end))
        assertEquals(CheckpointCommit.Committed(backward.anchor, 9, false), dao.commit(backward))
        assertEquals(CheckpointCommit.Committed(backward.anchor, 9, true), dao.commit(backward))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.CONFLICT), dao.commit(command(s, 9, AnchorPosition.End)))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(end))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.INVALID), dao.commit(command(s, 0)))
        assertEquals(backward.anchor, dao.read(key))
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url)); assertEquals(novel, db.novelDao().getById(1))
        assertEquals(legacy, db.chapterDao().getContent(1)); assertEquals(protocol, db.chapterDownloadStateDao().find(1))
    }

    @Test fun registrationSupersedesOnlyItsChapterAndRejectsWrongDocumentTokenOrAnchor() = runBlocking<Unit> {
        seed(); val old = session(); val first = command(old); dao.commit(first)
        val otherKey = key.copy(url = "/other")
        db.chapterDao().insertAll(listOf(metadata.copy(id = 2, url = otherKey.url)))
        val otherDoc = CanonicalDocument.fromPages(otherKey, pages)
        val other = requireNotNull(dao.register(requireNotNull(saved.readObserved(otherKey).observation), otherDoc))
        val newer = session()
        assertEquals(first.anchor, newer.checkpoint)
        assertEquals(first.anchor, dao.read(key))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(first))
        assertNull(dao.register(newer.observation, otherDoc))
        assertNull(dao.register(newer.observation, CanonicalDocument.fromPages(key, emptyList())))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.INVALID), dao.commit(command(newer).copy(anchor = command(other).anchor)))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.INVALID), dao.commit(command(newer).let { it.copy(anchor = it.anchor.copy(modelVersion = 99)) }))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(command(newer.copy(id = "invented"))))
        assertTrue(dao.commit(command(newer)) is CheckpointCommit.Committed)
        assertTrue(dao.commit(command(other)) is CheckpointCommit.Committed)
    }

    @Test fun contentIdentityRestoreRelockAndRecreationAbaRevokeEvenExactRetries() = runBlocking<Unit> {
        seed(); var s = session(); dao.commit(command(s))
        val sql = db.openHelper.writableDatabase
        val mutations: List<suspend () -> Unit> = listOf(
            { db.chapterDao().upsertContent(ChapterContentEntity(1, legacy)) },
            { db.chapterDao().upsertContent(ChapterContentEntity(1, legacy)) },
            { db.chapterDao().upsertContent(ChapterContentEntity(1, StoredChapterContent("Different", 1))); db.chapterDao().upsertContent(ChapterContentEntity(1, legacy)) },
            { db.chapterDao().deleteContent(1) },
            { db.chapterDao().restoreChapters(listOf(requireNotNull(db.chapterDao().getByUrl(1, key.url)))) },
            { sql.execSQL("UPDATE chapters SET locked=1 WHERE id=1"); sql.execSQL("UPDATE chapters SET locked=0 WHERE id=1") },
            { sql.execSQL("UPDATE novels SET sourceId=18 WHERE id=1"); sql.execSQL("UPDATE novels SET sourceId=17 WHERE id=1") },
            { sql.execSQL("DELETE FROM chapters WHERE id=1"); db.chapterDao().insertAll(listOf(metadata)) })
        for (mutate in mutations) {
            mutate()
            assertNull(dao.register(s.observation, document))
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(command(s)))
            s = session(); assertTrue(dao.commit(command(s)) is CheckpointCommit.Committed)
        }
        // Supported in-place identity migrations must not trap the new key behind old evidence.
        var currentKey = key
        db.novelDao().upsert(NovelEntity(id = 2, sourceId = 19, url = "/second-book", title = "Second book"))
        for ((statement, newKey) in listOf(
            "UPDATE chapters SET url='/renamed' WHERE id=1" to key.copy(url = "/renamed"),
            "UPDATE novels SET sourceId=18 WHERE id=1" to key.copy(novel = key.novel.copy(sourceId = 18), url = "/renamed"),
            "UPDATE novels SET url='/renamed-book' WHERE id=1" to ChapterKey(NovelKey(18, "/renamed-book"), "/renamed"),
            "UPDATE chapters SET novelId=2 WHERE id=1" to ChapterKey(NovelKey(19, "/second-book"), "/renamed"))) {
            val old = requireNotNull(dao.register(requireNotNull(saved.readObserved(currentKey).observation),
                CanonicalDocument.fromPages(currentKey, pages)))
            dao.commit(command(old))
            sql.execSQL(statement)
            assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(command(old)))
            assertNull(dao.read(newKey))
            val fresh = requireNotNull(dao.register(requireNotNull(saved.readObserved(newKey).observation),
                CanonicalDocument.fromPages(newKey, pages)))
            assertNull(dao.read(newKey))
            assertTrue(dao.commit(command(fresh)) is CheckpointCommit.Committed)
            currentKey = newKey
        }
    }

    @Test fun reopenAndOtherObserverCannotReuseAuthorityButRawCheckpointSurvives() = runBlocking<Unit> {
        seed(true); val s = session(); val written = command(s, 3); dao.commit(written)
        val foreign = RoomReadingCheckpointDao(db, RoomSavedChapterDao(db))
        assertNull(foreign.register(s.observation, document))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), foreign.commit(written))
        db.close(); open()
        assertEquals(written.anchor, dao.read(key))
        assertNull(dao.register(s.observation, document))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(written))
        assertTrue(dao.commit(command(session())) is CheckpointCommit.Committed)
    }

    @Test fun sqlFailureAndRealTransactionCancellationRollBackButLostReplyReplays() = runBlocking<Unit> {
        seed(); val s = session(); val first = command(s); dao.commit(first)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER reject_checkpoint BEFORE UPDATE ON reading_checkpoints BEGIN SELECT RAISE(ABORT,'checkpoint fault'); END")
        try { dao.commit(command(s, 2)); fail("SQL fault was ignored") } catch (_: android.database.sqlite.SQLiteException) { }
        try { dao.register(s.observation, document); fail("registration fault was ignored") } catch (_: android.database.sqlite.SQLiteException) { }
        sql.execSQL("DROP TRIGGER reject_checkpoint")
        assertEquals(first.anchor, dao.read(key))
        val wrote = CompletableDeferred<Unit>()
        val child = launch(Dispatchers.IO) { db.withTransaction { dao.register(s.observation, document); wrote.complete(Unit); awaitCancellation() } }
        withTimeout(10_000) { wrote.await() }; child.cancelAndJoin()
        val writing = CompletableDeferred<Unit>()
        val canceledWrite = launch(Dispatchers.IO) { db.withTransaction {
            dao.commit(command(s, 2, AnchorPosition.End)); writing.complete(Unit); awaitCancellation()
        } }
        withTimeout(10_000) { writing.await() }; canceledWrite.cancelAndJoin()
        assertEquals(first.anchor, dao.read(key))
        // Registration was rolled back; the original session remains current.
        val uncertain = command(s, 2, AnchorPosition.End)
        val lost = launch { dao.commit(uncertain); currentCoroutineContext().cancel(); yield(); fail("reply must be canceled") }
        lost.join(); assertTrue(lost.isCancelled)
        assertEquals(CheckpointCommit.Committed(uncertain.anchor, 2, true), dao.commit(uncertain))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.CONFLICT), dao.commit(command(s, 2)))
        val original = requireNotNull(db.readingCheckpointStateDao().find(1))
        val unknown = original.state.replace("\"format\":1", "\"format\":99")
        sql.execSQL("UPDATE reading_checkpoints SET state=? WHERE chapterId=1", arrayOf(unknown))
        try { dao.register(s.observation, document); fail("Unsupported state was overwritten") } catch (_: IllegalArgumentException) { }
        assertEquals(unknown, db.readingCheckpointStateDao().find(1)!!.state)
    }

    @Test fun downloadClaimsDoNotRevokeAbsenceUntilContentActuallyChanges() = runBlocking<Unit> {
        seed(); val s = session()
        val protocol = requireNotNull(db.chapterDownloadStateDao().find(1)).copy(activeAttemptId = "active", activeOwner = "owner", baseRevision = s.observation.generation)
        db.chapterDownloadStateDao().put(protocol)
        assertTrue(dao.commit(command(s)) is CheckpointCommit.Committed)
        assertEquals(protocol, db.chapterDownloadStateDao().find(1)); assertNull(db.chapterDao().getContent(1))
        db.chapterDao().upsertContent(ChapterContentEntity(1, legacy))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(command(s)))
        db.chapterDao().updateChapterMeta(1, key.url, "Chapter", 0, -1f, null, true)
        assertNull(dao.read(key)); assertNull(dao.read(key.copy(url = "/missing")))
    }

    @Test fun actualDb34ExpansionIsAtomicAndPreservesSchemaBodyProgressAndDownloadState() = runBlocking<Unit> {
        context.getDatabasePath(name).parentFile!!.mkdirs()
        val definitions: List<String>
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { raw ->
            val schema = instrumentation.context.assets.open("chapter-codec/v34.sql").bufferedReader().use { it.readText() }
                .lineSequence().filterNot { it.startsWith("--") }.joinToString("\n")
            // Trigger bodies contain semicolons; execute the frozen definitions one complete line at a time.
            for (statement in schema.lineSequence().filter { it.isNotBlank() }) raw.execSQL(statement)
            raw.execSQL("INSERT INTO novels(id,sourceId,url,title,genres,status,favorite,lastUpdated,chapterSortOrder,lastReadAt,lastAccessedAt,readerTextAlign,notifyOnNewChapters,notifyOnNewLockedChapters,autoDownloadNewChapters) VALUES(1,17,'/book','Book','',0,1,0,0,0,0,0,0,0,0)")
            raw.execSQL("INSERT INTO chapters(id,novelId,url,name,uploadDate,chapterNumber,translator,read,readProgress,readAnchorItemIndex,readAnchorItemOffset,downloadStatus,queueOrder,firstReadAt,wordCount,locked) VALUES(1,1,'/chapter','Chapter',0,-1,NULL,1,0.625,3,17,0,0,789,7,0)")
            raw.execSQL("INSERT INTO chapter_content VALUES(1,?,1)", arrayOf(legacy.content))
            raw.execSQL("INSERT INTO chapter_download_state(chapterId,revision,committedAttemptId,cacheRevision) VALUES(1,'generation','receipt','cache')")
            raw.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42", null).use { it.moveToFirst(); assertEquals("48b6a00fea18bc03786b997000ee932e", it.getString(0)) }
            definitions = raw.rawQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        }
        open(object : Migration(34, 35) { override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_34_35.migrate(db); error("Migration fault") } })
        try { db.openHelper.writableDatabase; fail("migration must roll back") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Migration fault")) }
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(34, raw.version)
            raw.rawQuery("SELECT count(*) FROM sqlite_master WHERE name='reading_checkpoints'", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
        open(); assertEquals(35, db.openHelper.writableDatabase.version)
        assertEquals(metadata, db.chapterDao().getByUrl(1, key.url)); assertEquals(legacy, db.chapterDao().getContent(1))
        assertEquals("receipt", db.chapterDownloadStateDao().find(1)!!.committedAttemptId)
        assertEquals("cache", db.chapterDownloadStateDao().find(1)!!.cacheRevision); assertNull(dao.read(key))
        val after = db.openHelper.readableDatabase.query("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT IN ('reading_checkpoints','room_master_table','checkpoint_chapter_identity','checkpoint_novel_identity') ORDER BY name").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertEquals(definitions, after)
    }
}
