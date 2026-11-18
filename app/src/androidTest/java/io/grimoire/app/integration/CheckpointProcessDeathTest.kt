package io.grimoire.app.integration

import android.os.Build
import android.os.Process
import android.util.AtomicFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.DOWNLOAD_SCHEMA_CALLBACK
import io.grimoire.app.data.local.RoomReadingCheckpointDao
import io.grimoire.app.data.local.RoomSavedChapterDao
import io.grimoire.app.data.local.entity.ChapterContentEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import io.grimoire.app.data.local.entity.encodeChapterContent
import io.grimoire.app.testing.CheckpointWriterProvider
import io.grimoire.app.ui.screen.reader.ReaderPosition
import io.grimoire.app.ui.screen.reader.ReaderProgressWriter
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.AnchorPosition
import io.grimoire.reading.anchor.ProjectedPage
import io.grimoire.reading.anchor.ReaderCheckpointLoad
import io.grimoire.reading.anchor.ReaderCheckpoints
import io.grimoire.reading.anchor.ReaderViewport
import io.grimoire.reading.anchor.ReadingProjection
import io.grimoire.reading.storage.CheckpointCommand
import io.grimoire.reading.storage.CheckpointCommit
import io.grimoire.reading.storage.CheckpointRejection
import io.grimoire.reading.storage.ReadingCheckpointDao
import io.grimoire.reading.storage.decodeReadingCheckpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Executed in separate invocations by android_integration.py. See docs/android-integration.md. */
@RunWith(AndroidJUnit4::class)
class CheckpointProcessDeathTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val target = CheckpointWriterProvider.target
    private val novel = target.novel
    private val novelMetadata = NovelEntity(id = 1, sourceId = novel.sourceId, url = novel.url, title = "Fixture")
    private val neighbor = ChapterKey(novel, "/neighbor")
    private val targetMetadata = ChapterEntity(
        id = 1, novelId = 1, url = target.url, name = "Target",
        read = true, readProgress = 0.625f, readAnchorItemIndex = 3, readAnchorItemOffset = 17, firstReadAt = 789,
    )
    private val neighborMetadata = targetMetadata.copy(id = 2, url = neighbor.url, name = "Neighbor", read = false)
    private val pages = (0..3).map { index -> NovelPage(index, PageContent.Text("Checkpoint block $index.")) }
    private val content = encodeChapterContent(pages)
    private val history = ReadingHistoryEntity(
        id = 1, sourcePackage = "checkpoint.fixture", novelId = 1, novelUrl = novel.url, novelTitle = "Fixture",
        chapterUrl = target.url, chapterName = targetMetadata.name, openedAt = 1234,
    )
    private lateinit var boundary: String
    private lateinit var fixture: String

    @Before fun verifyFixture() {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(args.getString("integrationFixtures") == "true")
        assertEquals(BuildConfig.GIT_SHA, args.getString("integrationSourceSha"))
        boundary = requireNotNull(args.getString("checkpointCrashBoundary"))
        require(boundary in setOf("during-debounce", "before-commit", "before-ack", "competing-writer"))
        fixture = requireNotNull(args.getString("checkpointCrashFixture"))
        require(fixture.matches(Regex("[a-f0-9]{32}")))
    }

    private fun marker(stage: String) = AtomicFile(File(context.filesDir, "checkpoint-$fixture-$stage.json"))
    private fun database(name: String = "checkpoint-$fixture.db") = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(DOWNLOAD_SCHEMA_CALLBACK)
        .build()

    private fun projection(key: ChapterKey) = ReadingProjection.create(
        key, pages, pages.mapIndexed { index, page -> ProjectedPage(index, page) },
    )

    private suspend fun seed(db: AppDatabase) {
        db.novelDao().upsert(novelMetadata)
        db.chapterDao().insertAll(listOf(targetMetadata, neighborMetadata))
        for (id in listOf(1L, 2L)) db.chapterDao().upsertContent(ChapterContentEntity(id, content))
        db.readingHistoryDao().upsert(history)
    }

    @Test fun interruptCheckpointSave() = runBlocking<Unit> {
        check(boundary != "competing-writer")
        check(!context.getDatabasePath("checkpoint-$fixture.db").exists())
        val db = database()
        try {
            seed(db)
            val saved = RoomSavedChapterDao(db)
            val dao = RoomReadingCheckpointDao(db, saved)
            val checkpoints = ReaderCheckpoints(object : ReadingCheckpointDao by dao {
                override suspend fun commit(command: CheckpointCommand): CheckpointCommit {
                    if (command.sequence == 1L || boundary == "during-debounce") return dao.commit(command)
                    assertEquals(target, command.anchor.key)
                    assertEquals(projection(target).capture(3), command.anchor)
                    assertEquals(2L, command.sequence)
                    // Nest the same real DAO write to pause after SQL but before the outer commit.
                    if (boundary == "before-commit") db.withTransaction {
                        assertEquals(CheckpointCommit.Committed(command.anchor, 2, false), dao.commit(command))
                        assertTrue(db.inTransaction())
                        assertEquals(command.anchor, dao.read(target))
                        terminate("uncommitted")
                    }
                    // Withhold the committed result from ReaderCheckpoints.flush().
                    assertEquals(CheckpointCommit.Committed(command.anchor, 2, false), dao.commit(command))
                    terminate("committed")
                }
            })
            for ((key, ordinal) in listOf(neighbor to 2, target to 1)) {
                val load = checkpoints.open(projection(key), saved.readObserved(key).observation, null)
                checkpoints.userPosition(load)
                assertEquals(projection(key).capture(ordinal), checkpoints.capture(load, ordinal))
                checkpoints.flush()
                assertEquals(projection(key).capture(ordinal), dao.read(key))
                if (key == target) {
                    if (boundary == "during-debounce") interruptDuringDebounce(db, checkpoints, load)
                    assertEquals(projection(key).capture(3), checkpoints.capture(load, 3))
                    checkpoints.flush()
                    fail("The interrupted save returned to its caller")
                }
            }
        } finally { db.close() }
    }

    private suspend fun interruptDuringDebounce(
        db: AppDatabase,
        checkpoints: ReaderCheckpoints,
        load: ReaderCheckpointLoad,
    ): Nothing = withContext(Dispatchers.Main.immediate) {
        var writeStarted = false
        val writer = ReaderProgressWriter(this) { position ->
            writeStarted = true
            checkpoints.flush()
            db.chapterDao().setReadAnchor(position.chapterId, position.fraction, position.anchorIndex, position.anchorOffset)
        }
        val anchor = requireNotNull(checkpoints.capture(load, 3))
        val pendingOrdinal = (anchor.position as AnchorPosition.Block).ordinal
        assertEquals(3, pendingOrdinal)
        writer.submit(ReaderPosition(1, 0.8f, 3, 0, contentAnchor = anchor, checkpoint = load))
        // The timer cannot resume on Main while this block checks the queue and terminates.
        assertTrue(writer.hasPending())
        assertFalse(writeStarted)
        terminate("pending", JSONObject().put("pendingOrdinal", pendingOrdinal).put("writeStarted", writeStarted))
    }

    private suspend fun terminate(stage: String, details: JSONObject = JSONObject()): Nothing {
        writeEvidence("boundary", details.put("stage", stage))
        Process.killProcess(Process.myPid())
        awaitCancellation()
    }

    @Test fun verifyCheckpointAfterRestart() = runBlocking<Unit> {
        check(boundary != "competing-writer")
        val oldPid = JSONObject(marker("boundary").readFully().decodeToString()).getInt("pid")
        assertNotEquals(oldPid, Process.myPid())
        assertTrue(context.getDatabasePath("checkpoint-$fixture.db").isFile)
        val db = database()
        try {
            db.openHelper.readableDatabase.query("PRAGMA quick_check").use { result ->
                assertTrue(result.moveToFirst())
                assertEquals("ok", result.getString(0))
            }
            val expected = if (boundary == "before-ack") 3 else 1
            val saved = RoomSavedChapterDao(db)
            val dao = RoomReadingCheckpointDao(db, saved)
            val targetState = decodeReadingCheckpoint(requireNotNull(db.readingCheckpointStateDao().find(1)).state)
            assertEquals(if (expected == 1) 1L else 2L, targetState.sequence)
            assertEquals(projection(target).capture(expected), dao.read(target))
            assertEquals(projection(neighbor).capture(2), dao.read(neighbor))
            val checkpoints = ReaderCheckpoints(dao)
            val reopened = checkpoints.open(projection(target), saved.readObserved(target).observation, null)
            assertEquals(ReaderViewport(expected), reopened.restore.viewport)
            assertNull(reopened.restore.notice)
            assertNull(checkpoints.capture(reopened, 0))
            checkpoints.flush()
            assertEquals(projection(target).capture(expected), dao.read(target))
            assertEquals(projection(neighbor).capture(2), dao.read(neighbor))
            assertOriginalLibrary(db)
            writeEvidence("reopened", JSONObject().put("oldPid", oldPid).put("ordinal", expected).put("neighborOrdinal", 2))
        } finally { db.close() }
    }

    @Test fun competingProcessCannotReplaceNewerCheckpoint() = runBlocking<Unit> {
        check(boundary == "competing-writer")
        val name = CheckpointWriterProvider.databaseName(fixture)
        check(!context.getDatabasePath(name).exists())
        val db = database(name)
        try {
            seed(db)
            val saved = RoomSavedChapterDao(db)
            val dao = RoomReadingCheckpointDao(db, saved)
            suspend fun save(key: ChapterKey, ordinal: Int): CheckpointCommand {
                val projection = projection(key)
                val session = requireNotNull(dao.register(requireNotNull(saved.readObserved(key).observation),
                    requireNotNull(projection.document)))
                val command = CheckpointCommand(session, 1, requireNotNull(projection.capture(ordinal)))
                assertEquals(CheckpointCommit.Committed(command.anchor, 1, false), dao.commit(command))
                return command
            }

            val neighborCommand = save(neighbor, 2)
            val original = save(target, 3)
            val stale = original.copy(sequence = 2, anchor = requireNotNull(projection(target).capture(2)))
            val client = requireNotNull(context.contentResolver.acquireContentProviderClient(context.packageName + ".checkpoint-fixture"))
            client.use {
                // The stable client and the provider's open Room instance keep both processes live.
                try {
                    val replaced = requireNotNull(client.call("advance", fixture, null))
                    val remotePid = replaced.getInt("pid")
                    assertTrue(remotePid > 0)
                    assertNotEquals(Process.myPid(), remotePid)
                    assertEquals(3, replaced.getInt("previousOrdinal"))
                    val checkpoint = requireNotNull(replaced.getString("checkpoint"))
                    val winner = decodeReadingCheckpoint(checkpoint)
                    val anchor = requireNotNull(winner.anchor)
                    assertNotEquals(original.session.binding.id, winner.binding.id)
                    assertNotEquals(original.session.observation.scope, winner.binding.observation.scope)
                    assertEquals(1L, winner.sequence)
                    assertEquals(projection(target).capture(1), anchor)
                    assertTrue(saved.isObservationCurrent(original.session.observation))
                    assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), dao.commit(stale))
                    assertEquals(anchor, dao.read(target))
                    assertEquals(checkpoint, db.readingCheckpointStateDao().find(1)?.state)
                    assertEquals(CheckpointCommit.Committed(neighborCommand.anchor, 1, true), dao.commit(neighborCommand))
                    val neighborAnchor = requireNotNull(dao.read(neighbor))
                    assertEquals(neighborCommand.anchor, neighborAnchor)
                    assertOriginalLibrary(db)

                    val remoteRead = requireNotNull(client.call("read", fixture, null))
                    assertEquals(remotePid, remoteRead.getInt("pid"))
                    assertEquals(checkpoint, remoteRead.getString("checkpoint"))
                    writeEvidence("competition", JSONObject().put("remotePid", remotePid)
                        .put("oldOrdinal", replaced.getInt("previousOrdinal"))
                        .put("winnerOrdinal", (anchor.position as AnchorPosition.Block).ordinal)
                        .put("neighborOrdinal", (neighborAnchor.position as AnchorPosition.Block).ordinal)
                        .put("winnerSequence", winner.sequence).put("staleSequence", stale.sequence)
                        .put("rejection", CheckpointRejection.STALE.name))
                } finally { client.call("close", fixture, null) }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun assertOriginalLibrary(db: AppDatabase) {
        assertEquals(novelMetadata, db.novelDao().getById(1))
        assertEquals(targetMetadata, db.chapterDao().getByUrl(1, target.url))
        assertEquals(neighborMetadata, db.chapterDao().getByUrl(1, neighbor.url))
        for (id in listOf(1L, 2L)) assertEquals(content, db.chapterDao().getContent(id))
        assertEquals(listOf(history), db.readingHistoryDao().getAll().first())
    }

    private fun writeEvidence(stage: String, details: JSONObject) {
        val data = details.put("pid", Process.myPid()).put("boundary", boundary)
            .put("fixture", fixture).put("sourceSha", BuildConfig.GIT_SHA)
        val file = marker(stage)
        val stream = file.startWrite()
        try {
            stream.write(data.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}
