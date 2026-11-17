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
import io.grimoire.app.data.reading.CompletionSaveNotice
import io.grimoire.app.data.tts.TtsChapterInput
import io.grimoire.app.data.tts.TtsCompletionWriter
import io.grimoire.app.data.tts.TtsUtteranceCallbacks
import io.grimoire.app.ui.screen.reader.ReaderContent
import io.grimoire.app.ui.screen.reader.ReaderVisualWriter
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

    @Test fun novelMarkAllOrdersSameValueUnreadAndPreservesFieldsAndOtherNovel() = runBlocking<Unit> {
        seed()
        val visual = automatic(session(ReadingStateWriterKind.VISUAL))
        val audio = automatic(session(ReadingStateWriterKind.AUDIO))
        // Both rows with the same source URL are included because the platform pins the row identity.
        val locked = metadata.copy(id = 2, name = "Locked duplicate URL", locked = true, firstReadAt = 42, queueOrder = 10)
        val another = metadata.copy(id = 3, novelId = 2, read = true, firstReadAt = 44)
        val blankUrl = metadata.copy(id = 4, url = "", name = "Legacy row without source URL")
        db.novelDao().upsert(NovelEntity(id = 2, sourceId = key.novel.sourceId, url = "/other", title = "Other"))
        db.chapterDao().insertAll(listOf(locked, another, blankUrl))
        val chapters = db.chapterDao()
        val facade = NovelReadingCompletionFacade(dao, null)
        val unread = facade.prepare(NovelReadingCompletionIntent(key.novel, false, 100))!!
        assertEquals(NovelReadingCompletionCommit.Committed(false), facade.submit(unread).confirmation)
        assertEquals(listOf(metadata, locked, blankUrl), chapters.getChapterMetadataOnce(1))
        rejected(dao.commit(visual), ReadingStateRejection.STALE)
        rejected(dao.commit(audio), ReadingStateRejection.STALE)
        for (id in listOf(1L, 2L, 4L)) {
            val state = decodeReadingStateRecord(db.readingStateStorageDao().find(id)!!.state)
            assertEquals(1L, state.intentRevision)
            assertNull(state.visual)
            assertNull(state.audio)
        }
        val read = facade.prepare(NovelReadingCompletionIntent(key.novel, true, 200))!!
        assertEquals(NovelReadingCompletionCommit.Committed(false), facade.submit(read).confirmation)
        assertEquals(
            listOf(metadata.copy(read = true, firstReadAt = 200), locked.copy(read = true),
                blankUrl.copy(read = true, firstReadAt = 200)),
            chapters.getChapterMetadataOnce(1),
        )
        assertEquals(listOf(another), chapters.getChapterMetadataOnce(2))
        assertNull(db.readingStateStorageDao().find(3))
        assertEquals(legacy, chapters.getContent(1))
    }

    @Test fun novelMarkAllRollsBackEarlierRowsAndReceiptsWhenLaterWriteFails() = runBlocking<Unit> {
        seed()
        val chapters = db.chapterDao()
        chapters.insertAll(listOf(metadata.copy(id = 2, url = "/second")))
        val intent = NovelReadingCompletionIntent(key.novel, true, 200)
        val batch = dao.prepareNovelCompletion(intent)!!
        val storage = db.readingStateStorageDao()
        val original = chapters.getChapterMetadataOnce(1)
        val states = listOf(storage.find(1), storage.find(2))
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER batch_fault BEFORE INSERT ON reading_state_receipts WHEN NEW.chapterId=2 BEGIN SELECT RAISE(ABORT,'batch fault'); END")
        try {
            dao.commitNovelCompletion(batch)
            fail("Expected a failure after the first target")
        } catch (failure: Exception) {
            assertTrue(failure.message!!.contains("batch fault"))
        } finally {
            sql.execSQL("DROP TRIGGER batch_fault")
        }
        assertEquals(original, chapters.getChapterMetadataOnce(1))
        assertEquals(states, listOf(storage.find(1), storage.find(2)))
        assertNull(storage.receipt(1, intent.id))
        assertNull(storage.receipt(2, intent.id))
        assertEquals(NovelReadingCompletionCommit.Committed(false), dao.commitNovelCompletion(batch))
        assertEquals(original.map { it.copy(read = true, firstReadAt = 200) }, chapters.getChapterMetadataOnce(1))
    }

    @Test fun novelMarkAllRejectsMembershipDriftAndReplaysOnlyTheOriginalBatchAfterReopen() = runBlocking<Unit> {
        seed()
        val outdated = dao.prepareNovelCompletion(NovelReadingCompletionIntent(key.novel, true, 100))!!
        val second = metadata.copy(id = 2, url = "/second")
        db.chapterDao().insertAll(listOf(second))
        assertEquals(NovelReadingCompletionCommit.NotApplied(ReadingStateRejection.CONFLICT), dao.commitNovelCompletion(outdated))
        assertEquals(listOf(metadata, second), db.chapterDao().getChapterMetadataOnce(1))

        val batch = dao.prepareNovelCompletion(NovelReadingCompletionIntent(key.novel, true, 200))!!
        val lost = object : NovelReadingCompletionDao by dao {
            override suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit {
                assertEquals(NovelReadingCompletionCommit.Committed(false), dao.commitNovelCompletion(command))
                throw IOException("Lost batch reply")
            }
        }
        try {
            NovelReadingCompletionFacade(lost, null).submit(batch)
            fail("Expected a lost reply")
        } catch (failure: IOException) {
            assertEquals("Lost batch reply", failure.message)
        }
        val newcomer = metadata.copy(id = 3, url = "/later")
        db.chapterDao().insertAll(listOf(newcomer))
        assertTrue(dao.commit(explicit(prepare(), false)) is ReadingStateCommit.Committed)
        db.close()
        open()
        assertEquals(NovelReadingCompletionCommit.Committed(true), dao.commitNovelCompletion(batch))
        assertEquals(listOf(metadata.copy(firstReadAt = 200), second.copy(read = true, firstReadAt = 200), newcomer),
            db.chapterDao().getChapterMetadataOnce(1))
        assertNull(db.readingStateStorageDao().find(3))
        assertNull(db.readingStateStorageDao().receipt(3, batch.intent.id))
    }

    @Test fun novelWordCountRetryDoesNotResubmitConfirmedBatch() = runBlocking<Unit> {
        seed()
        db.chapterDao().setWordCount(1, 0)
        var commits = 0
        val counted = object : NovelReadingCompletionDao by dao {
            override suspend fun commitNovelCompletion(command: PreparedNovelReadingCompletion): NovelReadingCompletionCommit {
                commits++
                return dao.commitNovelCompletion(command)
            }
        }
        var broken = true
        var backfills = 0
        val facade = NovelReadingCompletionFacade(counted) {
            backfills++
            if (broken) throw IOException("Count unavailable")
            db.chapterDao().backfillWordCountsFromDownloads()
        }
        val batch = facade.prepare(NovelReadingCompletionIntent(key.novel, true, 200))!!
        val partial = facade.submit(batch)
        assertEquals(NovelReadingCompletionCommit.Committed(false), partial.confirmation)
        assertTrue(partial.wordCount is ReadingWordCountOutcome.Failed)
        dao.commit(explicit(prepare(), false))
        broken = false
        val recovered = facade.retryWordCount(partial)
        assertEquals(ReadingWordCountOutcome.Completed, recovered.wordCount)
        assertEquals(1, commits)
        assertEquals(2, backfills)
        assertEquals(metadata.copy(firstReadAt = 200, wordCount = 0), db.chapterDao().getByUrl(1, key.url))
    }

    private suspend fun audioInput() = TtsChapterInput.fromLoaded(
        ChapterLoader(SavedChapterRepository(saved)).load(key) { error("The saved fixture must supply the pages") }
    )

    @Test fun audioCallbackUsesOriginalLoadAndCannotUndoExplicitUnread() = runBlocking<Unit> {
        seed()
        val facade = ReadingStateFacade(dao, null)
        val writer = TtsCompletionWriter(this, dao, facade)
        val original = audioInput()
        val callbacks = TtsUtteranceCallbacks()
        writer.bind(original)

        callbacks.speak("generation:0:0")
        val paused = requireNotNull(callbacks.finish("generation:0:0", playing = false, atMillis = 100))
        assertNull(writer.complete(paused, 0, 1))
        assertEquals(ReadStatus.UNREAD, dao.read(key)!!.status)

        facade.submit(requireNotNull(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key))))
        callbacks.speak("generation:1:0")
        val stale = requireNotNull(callbacks.finish("generation:1:0", playing = true, atMillis = 200))
        requireNotNull(writer.complete(stale, 1, 2)).join()
        assertEquals(ReadingCompletionState(key, ReadStatus.UNREAD, null), dao.read(key))
        writer.retire()

        val replacement = StoredChapterContent("Replacement\u001c<b>Replacement</b>", 1)
        db.chapterDao().upsertContent(ChapterContentEntity(1, replacement))
        writer.bind(original)
        assertNull(writer.complete(stale, 1, 2))
        val fresh = audioInput()
        assertNotEquals(original.observation, fresh.observation)
        writer.bind(fresh)
        callbacks.speak("fresh:0:0")
        val completed = requireNotNull(callbacks.finish("fresh:0:0", playing = true, atMillis = 300))
        val first = requireNotNull(writer.complete(completed, 0, 1))
        assertSame(first, writer.complete(completed, 0, 1))
        first.join()
        assertEquals(ReadingCompletionState(key, ReadStatus.READ, 300), dao.read(key))
        assertEquals(0.625f, db.chapterDao().getByUrl(1, key.url)!!.readProgress, 0f)
        writer.retire()
        writer.retry().join()
        assertNull(decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio)
    }

    @Test fun audioPartialSaveRetriesReceiptAfterPlaybackRetiresAndChapterIsUnread() = runBlocking<Unit> {
        seed()
        var writes = 0
        val facade = ReadingStateFacade(dao) { if (++writes == 1) throw IOException("Backfill") }
        val writer = TtsCompletionWriter(this, dao, facade)
        writer.bind(audioInput())
        val callbacks = TtsUtteranceCallbacks()
        callbacks.speak("playing")
        val completed = requireNotNull(callbacks.finish("playing", playing = true, atMillis = 400))
        requireNotNull(writer.complete(completed, 0, 1)).join()
        assertEquals(CompletionSaveNotice.WORD_COUNT_FAILED, writer.notice.value)
        assertEquals(ReadingCompletionState(key, ReadStatus.READ, 400), dao.read(key))
        writer.retire()
        facade.submit(requireNotNull(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key))))
        writer.retry().join()
        assertNull(writer.notice.value)
        assertEquals(2, writes)
        assertEquals(ReadingCompletionState(key, ReadStatus.UNREAD, 400), dao.read(key))
        assertEquals(1L, prepare().binding.intentRevision)
    }

    @Test fun lateAudioRegistrationIsRetiredAfterItsPlaybackIsCancelled() = runBlocking<Unit> {
        seed()
        val reached = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val delayed = object : ReadingStateDao by dao {
            override suspend fun register(binding: ReadingStateBinding, content: ChapterStorageObservation,
                document: CanonicalDocument, kind: ReadingStateWriterKind): ReadingStateSession? {
                val session = dao.register(binding, content, document, kind)
                reached.complete(Unit)
                reply.await()
                return session
            }
        }
        coroutineScope {
            val writer = TtsCompletionWriter(this, delayed, ReadingStateFacade(delayed, null))
            val task = launch { writer.bind(audioInput()) }
            withTimeout(5_000) { reached.await() }
            writer.retire()
            task.cancelAndJoin()
            reply.complete(Unit)
        }
        assertNull(decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio)
        assertEquals(ReadStatus.UNREAD, dao.read(key)!!.status)
    }

    @Test fun retryCannotCreateAnUncommittedAudioWriteAfterRetirement() = runBlocking<Unit> {
        seed()
        val submitted = mutableListOf<ReadingStateCommand>()
        var blocked = true
        var retirementBlocked = true
        val failing = object : ReadingStateDao by dao {
            override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit {
                submitted.add(command)
                if (blocked) throw IOException("Before completion commit")
                return dao.commit(command)
            }
            override suspend fun retire(session: ReadingStateSession): Boolean {
                if (retirementBlocked) throw IOException("Before writer retirement")
                return dao.retire(session)
            }
        }
        val writer = TtsCompletionWriter(this, failing, ReadingStateFacade(failing, null))
        writer.bind(audioInput())
        val callbacks = TtsUtteranceCallbacks()
        callbacks.speak("playing")
        val completed = requireNotNull(callbacks.finish("playing", playing = true, atMillis = 500))
        requireNotNull(writer.complete(completed, 0, 1)).join()
        assertEquals(CompletionSaveNotice.SAVE_FAILED, writer.notice.value)
        writer.retire()
        blocked = false
        writer.retry().join()
        assertEquals(1, submitted.size)
        assertEquals(CompletionSaveNotice.SAVE_FAILED, writer.notice.value)
        assertEquals(ReadStatus.UNREAD, dao.read(key)!!.status)
        assertNotNull(decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio)

        retirementBlocked = false
        writer.retry().join()
        assertSame(submitted[0], submitted[1])
        assertEquals(CompletionSaveNotice.CHANGED, writer.notice.value)
        assertNull(decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio)
        assertEquals(ReadingCompletionState(key, ReadStatus.UNREAD, null), dao.read(key))
    }

    @Test fun failedAudioRetirementDoesNotBlockReplacementPlayback() = runBlocking<Unit> {
        seed()
        var failRetirement = true
        val failing = object : ReadingStateDao by dao {
            override suspend fun retire(session: ReadingStateSession): Boolean {
                if (failRetirement) throw IOException("Old writer still needs cleanup")
                return dao.retire(session)
            }
        }
        val writer = TtsCompletionWriter(this, failing, ReadingStateFacade(failing, null))
        val input = audioInput()
        writer.bind(input)
        val old = decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio!!.id
        writer.retire()
        writer.bind(input)
        val active = decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio!!.id
        assertNotEquals(old, active)
        failRetirement = false
        writer.retry().join()
        assertEquals(active, decodeReadingStateRecord(db.readingStateStorageDao().find(1)!!.state).audio!!.id)
        val callbacks = TtsUtteranceCallbacks()
        callbacks.speak("replacement")
        val completed = requireNotNull(callbacks.finish("replacement", playing = true, atMillis = 600))
        requireNotNull(writer.complete(completed, 0, 1)).join()
        assertEquals(600L, dao.read(key)!!.firstReadAt)
        writer.retire()
    }

    @Test fun retiringDuringTheFirstAudioCommitSurfacesItsTerminalResult() = runBlocking<Unit> {
        seed()
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val retired = CompletableDeferred<Unit>()
        val delayed = object : ReadingStateDao by dao {
            override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit {
                entered.complete(Unit)
                proceed.await()
                return dao.commit(command)
            }
            override suspend fun retire(session: ReadingStateSession): Boolean =
                dao.retire(session).also { retired.complete(Unit) }
        }
        val writer = TtsCompletionWriter(this, delayed, ReadingStateFacade(delayed, null))
        writer.bind(audioInput())
        val callbacks = TtsUtteranceCallbacks()
        callbacks.speak("playing")
        val completed = requireNotNull(callbacks.finish("playing", playing = true, atMillis = 700))
        val saving = requireNotNull(writer.complete(completed, 0, 1))
        try {
            withTimeout(5_000) { entered.await() }
            writer.retire()
            withTimeout(5_000) { retired.await() }
        } finally {
            proceed.complete(Unit)
        }
        saving.join()
        assertEquals(CompletionSaveNotice.CHANGED, writer.notice.value)
        assertEquals(ReadingCompletionState(key, ReadStatus.UNREAD, null), dao.read(key))
    }

    @Test fun visualOwnerCannotRelabelOldSamplesAfterExplicitIntentOrReplacement() = runBlocking<Unit> {
        seed()
        val facade = ReadingStateFacade(dao, null)
        val queue = ReadingCompletionCommands(facade)
        val writer = ReaderVisualWriter(dao, facade)
        val projection = ReadingProjection.create(key, pages, listOf(ProjectedPage(0, pages[0])))
        val load = ReaderContent(1, projection, pages, saved.readObserved(key).observation)
        val end = requireNotNull(AndroidViewportV1.create(0, 0, 1, 1f))
        writer.setTarget(load)
        writer.synchronize()
        val old = requireNotNull(writer.authority(projection))
        val queued = requireNotNull(writer.capture(old, projection, end, AutomaticReadPolicy.AtEnd, 100))
        facade.submit(requireNotNull(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key))))
        queue.acceptAutomatic(queued)
        val result = (queue.flush().single().outcome as ReadingCommandOutcome.Saved).save
        assertEquals(ReadingStateConfirmation.NotApplied(ReadingStateRejection.STALE), result.confirmation)
        assertEquals(ReadStatus.UNREAD, result.current!!.status)

        writer.invalidate(old.id)
        writer.setTarget(load)
        writer.synchronize()
        val fresh = requireNotNull(writer.authority(projection))
        assertNotEquals(old.id, fresh.id)
        assertNull(writer.capture(old, projection, end, AutomaticReadPolicy.AtEnd, 100))
        val current = requireNotNull(writer.capture(fresh, projection, end, AutomaticReadPolicy.AtEnd, 200))
        queue.acceptAutomatic(current)
        assertEquals(200L, (queue.flush().single().outcome as ReadingCommandOutcome.Saved).save.current!!.firstReadAt)
        assertEquals(1L, prepare().binding.intentRevision)

        writer.setTarget(null)
        writer.synchronize()
        assertNull(writer.authority(projection))
        rejected(dao.commit(current.copy(id = "late", sequence = 2)), ReadingStateRejection.STALE)
        assertTrue((dao.commit(current) as ReadingStateCommit.Committed).replayed)
    }

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

    @Test fun exactWriterRetirementPreservesOtherKindValuesAndReceipts() = runBlocking<Unit> {
        seed(); val visual = session(ReadingStateWriterKind.VISUAL); val audio = session(ReadingStateWriterKind.AUDIO)
        val accepted = automatic(visual); dao.commit(accepted)
        val before = db.chapterDao().getByUrl(1, key.url); val receipt = db.readingStateStorageDao().receipt(1, accepted.id)
        assertTrue(dao.retire(visual)); assertFalse(dao.retire(visual))
        rejected(dao.commit(automatic(visual, 2)), ReadingStateRejection.STALE)
        rejected(dao.commit(automatic(audio)), ReadingStateRejection.NO_CHANGE)
        assertTrue((dao.commit(accepted) as ReadingStateCommit.Committed).replayed)
        val newer = session(ReadingStateWriterKind.VISUAL)
        assertFalse(dao.retire(visual)); rejected(dao.commit(automatic(newer)), ReadingStateRejection.NO_CHANGE)
        assertTrue(dao.retire(audio)); assertEquals(before, db.chapterDao().getByUrl(1, key.url))
        assertEquals(receipt, db.readingStateStorageDao().receipt(1, accepted.id)); assertEquals(legacy, db.chapterDao().getContent(1))
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET url='/temporary' WHERE id=1")
        db.openHelper.writableDatabase.execSQL("UPDATE chapters SET url='/chapter' WHERE id=1")
        assertFalse(dao.retire(newer)); assertNull(db.readingStateStorageDao().find(1))
    }

    @Test fun retirementRollbackCancellationAndReopenNeverRevokeTheWrongOwner() = runBlocking<Unit> {
        seed(); val s = session(ReadingStateWriterKind.VISUAL); val storage = db.readingStateStorageDao(); val before = storage.find(1)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER retire_fault BEFORE UPDATE ON reading_states BEGIN SELECT RAISE(ABORT,'retire fault'); END")
        try { dao.retire(s); fail("Expected retirement fault") } catch (e: Exception) { assertTrue(e.message!!.contains("retire fault")) }
        assertEquals(before, storage.find(1)); assertEquals(metadata, db.chapterDao().getByUrl(1, key.url)); sql.execSQL("DROP TRIGGER retire_fault")
        val entered = CompletableDeferred<Unit>()
        val pending = launch(Dispatchers.Default) { db.withTransaction { assertTrue(dao.retire(s)); entered.complete(Unit); awaitCancellation() } }
        entered.await(); pending.cancelAndJoin(); assertEquals(before, storage.find(1))
        val other = Room.databaseBuilder(context, AppDatabase::class.java, name).addCallback(DOWNLOAD_SCHEMA_CALLBACK).build()
        try { assertFalse(RoomReadingStateDao(other, RoomSavedChapterDao(other)).retire(s)) } finally { other.close() }
        assertEquals(before, storage.find(1)); assertTrue(dao.commit(automatic(s)) is ReadingStateCommit.Committed)
        db.close(); open(); assertFalse(dao.retire(s))
        val fresh = session(ReadingStateWriterKind.VISUAL); assertTrue(dao.retire(fresh))
        val current = db.readingStateStorageDao().find(1)!!
        db.readingStateStorageDao().put(current.copy(state = current.state.replace("\"format\":1", "\"format\":99")))
        try { dao.retire(fresh); fail("Corruption cannot become a no-op") } catch (_: IllegalArgumentException) { }
    }

    @Test fun sharedFacadeReportsBackfillFailureAndRetriesWithoutUndoingUnread() = runBlocking<Unit> {
        seed(); db.chapterDao().setWordCount(1, 0); var broken = true
        val facade = ReadingStateFacade(dao) { if (broken) throw IOException("Backfill failed") else db.chapterDao().backfillWordCountsFromDownloads() }
        val command = facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 100), "read")!!
        val partial = facade.submit(command)
        assertTrue(partial.confirmation is ReadingStateConfirmation.Confirmed)
        assertTrue(partial.wordCount is ReadingWordCountOutcome.Failed); assertTrue(db.chapterDao().getByUrl(1, key.url)!!.read)
        facade.submit(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(key), "unread")!!); broken = false
        val replay = facade.submit(command)
        assertTrue((replay.confirmation as ReadingStateConfirmation.Confirmed).replayed)
        assertEquals(ReadStatus.READ, (replay.confirmation as ReadingStateConfirmation.Confirmed).valueAtCommit.status); assertEquals(ReadStatus.UNREAD, replay.current!!.status)
        assertEquals(ReadingWordCountOutcome.Completed, replay.wordCount); assertEquals(0, db.chapterDao().getByUrl(1, key.url)!!.wordCount)
        facade.submit(facade.prepareExplicit(ReadingCompletionInput.MarkRead(key, 201), "reread")!!)
        assertEquals(metadata.copy(read = true, firstReadAt = 100, wordCount = 1), db.chapterDao().getByUrl(1, key.url))
        assertEquals(legacy, db.chapterDao().getContent(1))
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
