package io.grimoire.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.backup.BackupChapter
import io.grimoire.app.data.backup.mergeBackupChapter
import io.grimoire.app.data.download.AndroidDownloadQueue
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadCommitTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "guarded-download-test.db"
    private val directory = File(context.cacheDir, "download-${UUID.randomUUID()}").apply { mkdirs() }
    private val owners = mutableListOf<DownloadWriteOwner>()
    private var database: AppDatabase? = null
    private val db get() = database!!
    private lateinit var owner: DownloadWriteOwner
    private lateinit var adapter: RoomDownloadDao
    private lateinit var repository: DownloadRepository
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val metadata = ChapterEntity(id = 1, novelId = 1, url = key.url, name = "Chapter",
        read = true, readProgress = 0.625f, readAnchorItemIndex = 3, readAnchorItemOffset = 17,
        firstReadAt = 789, wordCount = 7)
    private val pages = listOf(NovelPage(0, PageContent.Text("New faithful prose")))

    @After fun cleanup() {
        database?.close()
        owners.forEach { it.close() }
        context.deleteDatabase(name)
        directory.deleteRecursively()
    }

    private fun open() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_33_34, MIGRATION_34_35).addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        assertEquals(35, db.openHelper.writableDatabase.version)
        owner = DownloadWriteOwner(File(directory, "owner.lock")).also { owners += it }
        adapter = RoomDownloadDao(db, owner)
        repository = DownloadRepository(adapter)
    }

    private suspend fun fresh() {
        context.deleteDatabase(name)
        open()
        db.novelDao().upsert(NovelEntity(id = 1, sourceId = 17, url = "/book", title = "Book", favorite = true))
        db.chapterDao().insertAll(listOf(metadata))
    }

    private suspend fun claim(refresh: Boolean = false): DownloadAttempt {
        db.chapterDao().setDownloadStatus(1, if (refresh) 5 else 1)
        return requireNotNull(repository.claim(key))
    }

    @Test fun migratesFrozen33WithoutRewritingBodyProgressOrLegacyCacheIdentity() = runBlocking {
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { legacy ->
            val schema = instrumentation.context.assets.open("chapter-codec/v33.sql").bufferedReader().use { it.readText() }
                .lineSequence().filterNot { it.startsWith("--") }.joinToString("\n")
            schema.split(';').filter { it.isNotBlank() }.forEach(legacy::execSQL)
            legacy.execSQL("""INSERT INTO novels (id,sourceId,url,title,genres,status,favorite,lastUpdated,
                chapterSortOrder,lastReadAt,lastAccessedAt,readerTextAlign,notifyOnNewChapters,
                notifyOnNewLockedChapters,autoDownloadNewChapters)
                VALUES (1,17,'/book','Book','',0,1,0,0,0,0,0,0,0,0)""")
            legacy.execSQL("""INSERT INTO chapters (id,novelId,url,name,uploadDate,chapterNumber,read,
                readProgress,readAnchorItemIndex,readAnchorItemOffset,downloadStatus,queueOrder,firstReadAt,wordCount,locked)
                VALUES (1,1,'/chapter','Chapter',0,-1,1,0.625,3,17,3,0,789,7,0)""")
            legacy.execSQL("INSERT INTO chapter_content(chapterId,content,contentVersion) VALUES(1,?,1)", arrayOf("Old\u001c<i>Old</i>"))
            for ((id, status, url) in listOf(Triple(2, 1, "/locked-fresh"), Triple(3, 6, "/locked-refresh"))) {
                legacy.execSQL("""INSERT INTO chapters (id,novelId,url,name,uploadDate,chapterNumber,read,
                    readProgress,readAnchorItemIndex,readAnchorItemOffset,downloadStatus,queueOrder,firstReadAt,wordCount,locked)
                    SELECT ?,novelId,?,name,uploadDate,chapterNumber,read,readProgress,readAnchorItemIndex,
                    readAnchorItemOffset,?,queueOrder,firstReadAt,wordCount,1 FROM chapters WHERE id=1""", arrayOf(id, url, status))
            }
            legacy.execSQL("INSERT INTO chapter_content(chapterId,content,contentVersion) VALUES(3,'Keep refresh bytes',1)")
        }
        open()
        assertEquals(metadata.copy(downloadStatus = 3), db.chapterDao().getByUrl(1, key.url))
        assertEquals(metadata.copy(id = 2, url = "/locked-fresh", locked = true), db.chapterDao().getByUrl(1, "/locked-fresh"))
        assertEquals(metadata.copy(id = 3, url = "/locked-refresh", locked = true, downloadStatus = 3), db.chapterDao().getByUrl(1, "/locked-refresh"))
        assertEquals(StoredChapterContent("Keep refresh bytes", 1), db.chapterDao().getContent(3))
        assertEquals(0, db.chapterDao().getPendingDownloadCount())
        assertEquals(0, db.chapterDao().getQueuedCount())
        val before = db.chapterDao().getContent(1)!!
        assertEquals(StoredChapterContent("Old\u001c<i>Old</i>", 1), before)
        assertNull(db.chapterDownloadStateDao().find(1))
        val attempt = claim(true)
        assertNull(SavedChapterRepository(RoomSavedChapterDao(db)).readSnapshot(key)!!.cacheRevision)
        db.chapterDao().cancelDownloads(listOf(1))
        assertTrue(repository.commit(attempt, pages) is DownloadCommit.NotApplied)
        assertEquals(before, db.chapterDao().getContent(1))
        assertEquals(metadata.copy(downloadStatus = 3), db.chapterDao().getByUrl(1, key.url))
    }

    @Test fun commitReplayConflictPositiveAndZeroCountsSurviveReopen() = runBlocking {
        fresh()
        val attempt = claim()
        db.chapterDao().setReadAnchor(1, 0.875f, 8, 24)
        val result = repository.commit(attempt, pages) as DownloadCommit.Committed
        assertFalse(result.replayed)
        assertEquals(3, result.receipt.wordCount)
        assertEquals(metadata.copy(downloadStatus = 3, wordCount = 3, readProgress = 0.875f,
            readAnchorItemIndex = 8, readAnchorItemOffset = 24), db.chapterDao().getByUrl(1, key.url))
        assertEquals(result.copy(replayed = true), repository.commit(attempt, pages))
        try { repository.commit(attempt, emptyList()); fail("Conflicting replay accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(encodeChapterContent(pages), db.chapterDao().getContent(1))
        val refresh = claim(true)
        assertEquals(result.copy(replayed = true), repository.commit(attempt, pages))
        val zero = repository.commit(refresh, listOf(NovelPage(0, PageContent.Image("https://fixture.invalid/i")))) as DownloadCommit.Committed
        assertEquals(3, zero.receipt.wordCount)
        db.close(); owner.close(); open()
        assertEquals(zero.receipt, repository.currentReceipt(key))
        assertEquals(zero.copy(replayed = true), repository.commit(refresh, listOf(NovelPage(0, PageContent.Image("https://fixture.invalid/i")))))
    }

    @Test fun lateSuccessErrorAndConnectivityCannotChangeRequeuedFreshOrRefreshAttempts() = runBlocking {
        fresh()
        for (refresh in listOf(false, true)) {
            val a = claim(refresh)
            val oldContent = db.chapterDao().getContent(1)
            db.chapterDao().cancelDownloads(listOf(1))
            val b = claim(refresh)
            // The pre-change status-only guard would accept A here.
            assertEquals(if (refresh) 6 else 2, db.chapterDao().getDownloadStatus(1))
            assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(a, pages))
            for (kind in DownloadFailure.entries) assertFalse(repository.fail(a, kind))
            assertEquals(oldContent, db.chapterDao().getContent(1))
            assertEquals(b.id, db.chapterDownloadStateDao().find(1)!!.activeAttemptId)
            assertTrue(repository.commit(b, pages) is DownloadCommit.Committed)
        }
    }

    @Test fun allInvalidatingWritersPreventResurrectionAndKeepUnrelatedFields() = runBlocking {
        fresh()
        val actions: List<suspend () -> Unit> = listOf(
            { db.chapterDao().cancelDownloads(listOf(1)) }, { db.chapterDao().cancelAllQueued(1) },
            { db.chapterDao().cancelAllQueuedDownloads() }, { adapter.resetInterruptedDownloads() },
            { db.chapterDao().deleteDownload(1) },
            { db.chapterDao().setDownloadedContent(1, encodeChapterContent(pages), 3) },
            { db.openHelper.writableDatabase.execSQL("UPDATE chapters SET locked=1 WHERE id=1") },
            { db.openHelper.writableDatabase.execSQL("UPDATE novels SET url='/moved' WHERE id=1") },
            { db.chapterDao().replaceChaptersWithContent(1, listOf(metadata), listOf(encodeChapterContent(pages))) },
            { db.chapterDao().upsertAll(listOf(metadata.copy(readProgress = 0.75f))) },
        )
        for (action in actions) {
            db.openHelper.writableDatabase.execSQL("UPDATE chapters SET locked=0 WHERE id=1")
            db.openHelper.writableDatabase.execSQL("UPDATE novels SET url='/book' WHERE id=1")
            val a = claim()
            action()
            val body = db.chapterDao().getContent(1)
            assertTrue(repository.commit(a, pages) is DownloadCommit.NotApplied)
            assertFalse(repository.fail(a, DownloadFailure.ERROR))
            assertEquals(body, db.chapterDao().getContent(1))
        }
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET locked=0 WHERE id=1")
        db.openHelper.writableDatabase.execSQL("UPDATE novels SET url='/book' WHERE id=1")
        val committed = repository.commit(claim(), pages) as DownloadCommit.Committed
        db.chapterDao().deleteAllDownloads(1)
        assertNull(repository.currentReceipt(key))
        assertTrue(repository.commit(DownloadAttempt(key, committed.receipt.attemptId, "old"), pages) is DownloadCommit.NotApplied)
        assertNull(db.chapterDao().getContent(1))
        db.chapterDao().setDownloadedContent(1, encodeChapterContent(pages), 3)
        db.openHelper.writableDatabase.execSQL("UPDATE chapter_content SET content='broken', contentVersion=2 WHERE chapterId=1")
        val corrupt = db.chapterDao().getContent(1)
        val refresh = claim(true)
        try { repository.commit(refresh, pages); fail("Corruption was overwritten") } catch (_: IllegalArgumentException) { }
        assertTrue(repository.fail(refresh, DownloadFailure.ERROR))
        assertEquals(7, db.chapterDao().getDownloadStatus(1))
        assertEquals(corrupt, db.chapterDao().getContent(1))
    }

    @Test fun storageFailureAndRealInFlightCancellationRollBackButLostReplyDoesNot() = runBlocking {
        fresh()
        val attempt = claim()
        try {
            adapter.commit(attempt, PreparedDownload(StoredChapterContent("unsupported", 99), "bad", 0))
            fail("Invalid prepared payload accepted")
        } catch (_: IllegalArgumentException) { }
        assertNull(db.chapterDao().getContent(1))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_receipt BEFORE INSERT ON chapter_download_state " +
            "WHEN NEW.committedAttemptId IS NOT NULL BEGIN SELECT RAISE(ABORT, 'fixture receipt failure'); END")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_receipt_update BEFORE UPDATE ON chapter_download_state " +
            "WHEN NEW.committedAttemptId IS NOT NULL BEGIN SELECT RAISE(ABORT, 'fixture receipt failure'); END")
        try { repository.commit(attempt, pages); fail("Fault was ignored") } catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(db.chapterDao().getContent(1))
        assertEquals(2, db.chapterDao().getDownloadStatus(1))
        assertEquals(attempt.id, db.chapterDownloadStateDao().find(1)!!.activeAttemptId)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_receipt")
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_receipt_update")
        val wrote = CompletableDeferred<Unit>()
        var outcome: Result<Unit>? = null
        val child = launch(Dispatchers.IO) {
            outcome = runCatching { db.withTransaction {
                repository.commit(attempt, pages)
                wrote.complete(Unit)
                awaitCancellation()
            } }
        }
        withTimeout(10_000) { wrote.await() }
        child.cancelAndJoin()
        assertTrue(outcome?.exceptionOrNull() is CancellationException)
        assertNull(db.chapterDao().getContent(1))
        assertNull(repository.currentReceipt(key))
        val lostReply = DownloadRepository(object : DownloadDao by adapter {
            override suspend fun commit(attempt: DownloadAttempt, prepared: PreparedDownload): DownloadCommit {
                val result = adapter.commit(attempt, prepared)
                currentCoroutineContext().cancel()
                return result
            }
        })
        var lost: Result<DownloadCommit>? = null
        val committedChild = launch(start = CoroutineStart.UNDISPATCHED) { lost = runCatching { lostReply.commit(attempt, pages) } }
        committedChild.join()
        assertTrue(lost?.exceptionOrNull() is CancellationException)
        assertEquals(attempt.id, repository.currentReceipt(key)!!.attemptId)
        assertEquals(encodeChapterContent(pages), db.chapterDao().getContent(1))
    }

    @Test fun exclusiveOwnerResetAndOldOwnerCallbacksCannotClobberNewWork() = runBlocking {
        fresh()
        val a = claim()
        val competing = DownloadWriteOwner(File(directory, "owner.lock")).also { owners += it }
        try { competing.requireOwner(); fail("Second writer accepted") } catch (_: IllegalStateException) { }
        owner.close()
        owner = DownloadWriteOwner(File(directory, "owner.lock")).also { owners += it }
        adapter = RoomDownloadDao(db, owner)
        repository = DownloadRepository(adapter)
        adapter.resetInterruptedDownloads()
        val b = requireNotNull(repository.claim(key))
        assertNotEquals(a.id, b.id)
        assertTrue(repository.commit(a, pages) is DownloadCommit.NotApplied)
        assertFalse(repository.fail(a, DownloadFailure.CONNECTIVITY))
        assertTrue(repository.commit(b, pages) is DownloadCommit.Committed)
    }

    @Test fun coherentSavedSnapshotsAndRevisionImagesCannotAttachToNewOrDeletedContent() = runBlocking {
        fresh()
        val store = ChapterImageStore(object : ContextWrapper(context) {
            override fun getFilesDir(): File = File(directory, "images").apply { mkdirs() }
        })
        val images = pages + NovelPage(1, PageContent.Image("https://fixture.invalid/i"))
        db.chapterDao().setDownloadedContent(1, encodeChapterContent(images), 3)
        store.saveLocalImages(1, key.url, listOf(1 to byteArrayOf(1)))
        val saved = SavedChapterRepository(RoomSavedChapterDao(db))
        assertNull(saved.readSnapshot(key)!!.cacheRevision)
        assertNotNull(store.localImageUri(1, key.url, 1))
        val a = repository.commit(claim(true), images) as DownloadCommit.Committed
        val snapshotA = saved.readSnapshot(key)!!
        assertEquals(a.receipt.revision, snapshotA.cacheRevision)
        assertNull(store.localImageUri(1, key.url, 1, snapshotA.cacheRevision)) // no stale legacy fallback
        store.saveLocalImages(1, key.url, listOf(1 to byteArrayOf(2)), a.receipt.revision)
        val b = repository.commit(claim(true), images) as DownloadCommit.Committed
        assertNotEquals(a.receipt.revision, b.receipt.revision)
        store.saveLocalImages(1, key.url, listOf(1 to byteArrayOf(3)), a.receipt.revision) // late A
        val snapshotB = saved.readSnapshot(key)!!
        assertEquals(b.receipt.revision, snapshotB.cacheRevision)
        assertNull(store.localImageUri(1, key.url, 1, snapshotB.cacheRevision))
        store.saveLocalImages(1, key.url, listOf(1 to byteArrayOf(4)), b.receipt.revision)
        assertArrayEquals(byteArrayOf(3), store.localImageFile(1, key.url, 1, snapshotA.cacheRevision)!!.readBytes())
        assertArrayEquals(byteArrayOf(4), store.localImageFile(1, key.url, 1, snapshotB.cacheRevision)!!.readBytes())
        db.chapterDao().deleteDownload(1)
        store.saveLocalImages(1, key.url, listOf(1 to byteArrayOf(5)), b.receipt.revision)
        assertNull(saved.readSnapshot(key))
        val c = repository.commit(claim(), images) as DownloadCommit.Committed
        assertNull(store.localImageUri(1, key.url, 1, c.receipt.revision))
        store.deleteChapter(1, key.url)
    }

    @Test fun productionQueueUsesFacadeAndRetainsSourceAndLockGates() = runBlocking {
        fresh()
        val queue = AndroidDownloadQueue(db, repository, adapter)
        db.chapterDao().setDownloadStatus(1, 1)
        val claimed = requireNotNull(queue.take())
        assertEquals(key, claimed.attempt.key)
        assertEquals(2, db.chapterDao().getDownloadStatus(1))
        assertTrue(repository.fail(claimed.attempt, DownloadFailure.CONNECTIVITY))
        val again = requireNotNull(queue.take())
        assertNotEquals(claimed.attempt.id, again.attempt.id)
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET locked=1 WHERE id=1")
        assertEquals(0, db.chapterDao().getDownloadStatus(1))
        assertTrue(repository.commit(again.attempt, pages) is DownloadCommit.NotApplied)
        assertNull(queue.take())
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET locked=0, downloadStatus=1 WHERE id=1")
        val beforeMove = requireNotNull(queue.take())
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET url='/moved' WHERE id=1")
        assertEquals(1, db.chapterDao().getDownloadStatus(1))
        assertEquals(1, db.chapterDao().getQueuedCount())
        assertTrue(repository.commit(beforeMove.attempt, pages) is DownloadCommit.NotApplied)
        val moved = requireNotNull(queue.take())
        assertEquals("/moved", moved.attempt.key.url)
        db.openHelper.writableDatabase.execSQL("UPDATE novels SET sourceId=18, url='/new-book' WHERE id=1")
        assertEquals(1, db.chapterDao().getDownloadStatus(1))
        assertFalse(repository.fail(moved.attempt, DownloadFailure.CONNECTIVITY))
        val newNovel = requireNotNull(queue.take())
        assertEquals(NovelKey(18, "/new-book"), newNovel.attempt.key.novel)
        db.openHelper.writableDatabase.execSQL("UPDATE novels SET sourceId=0 WHERE id=1")
        assertEquals(0, db.chapterDao().getPendingDownloadCount())
        assertEquals(0, db.chapterDao().getQueuedCount())
        assertNull(queue.take())
        assertNull(repository.claim(ChapterKey(NovelKey(0, "/new-book"), "/moved")))
    }

    @Test fun productionRestoreMergeRevokesFreshAndRefreshOwnershipAtomically() = runBlocking {
        fresh()
        for (refresh in listOf(false, true)) {
            val a = claim(refresh)
            val prior = db.chapterDao().getByUrl(1, key.url)!!
            val body = db.chapterDao().getContent(1)
            val merged = mergeBackupChapter(BackupChapter(key.url, "Restored", readProgress = 0.9f, wordCount = 10), prior, 1)
            assertEquals(if (refresh) 6 else 2, merged.downloadStatus)
            try {
                db.withTransaction { db.chapterDao().restoreChapters(listOf(merged)); error("Abort restore") }
            } catch (_: IllegalStateException) { }
            assertEquals(prior, db.chapterDao().getByUrl(1, key.url))
            assertEquals(a.id, db.chapterDownloadStateDao().find(1)!!.activeAttemptId)
            db.chapterDao().restoreChapters(listOf(merged))
            assertEquals(if (refresh) 5 else 1, db.chapterDao().getDownloadStatus(1))
            assertNull(repository.currentReceipt(key))
            assertTrue(repository.commit(a, pages) is DownloadCommit.NotApplied)
            for (kind in DownloadFailure.entries) assertFalse(repository.fail(a, kind))
            assertEquals(body, db.chapterDao().getContent(1))
            assertEquals(0.9f, db.chapterDao().getByUrl(1, key.url)!!.readProgress)
            val b = requireNotNull(repository.claim(key))
            assertTrue(repository.commit(b, pages) is DownloadCommit.Committed)
            assertEquals(0.9f, db.chapterDao().getByUrl(1, key.url)!!.readProgress)
        }
    }

}
