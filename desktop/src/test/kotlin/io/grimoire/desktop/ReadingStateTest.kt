package io.grimoire.desktop

import io.grimoire.reading.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReadingStateTest {
    @get:Rule val temporary = TemporaryFolder()
    private val document = CanonicalDocument.fromPages(fixtureKey, prose())
    private suspend fun prepare(dao: ReadingStateDao) = requireNotNull(dao.prepare(fixtureKey))
    private fun explicit(p: PreparedReadingState, read: Boolean, id: String = UUID.randomUUID().toString()) = ReadingStateCommand(id, p.binding,
        if (read) ReadingCompletionInput.MarkRead(fixtureKey, 100) else ReadingCompletionInput.MarkUnread(fixtureKey), null, 0)
    private suspend fun session(library: LocalLibrary, kind: ReadingStateWriterKind): ReadingStateSession {
        val dao = DesktopReadingStateDao(library)
        return requireNotNull(dao.register(prepare(dao).binding, requireNotNull(DesktopSavedChapterDao(library).readObserved(fixtureKey).observation), document, kind))
    }
    private fun automatic(s: ReadingStateSession, sequence: Long = 1): ReadingStateCommand {
        val input = if (s.kind == ReadingStateWriterKind.VISUAL) {
            val p = ReadingProjection.create(fixtureKey, prose(), listOf(ProjectedPage(0, prose()[0])))
            ReadingCompletionInput.Visual(fixtureKey, VisualCompletionEvidence(s.id, s.content, document.logicalRevision, AndroidViewportV1.create(0, 0, 1, 1f)!!),
                VisualCompletionContext(s.id, s.content, p, true, true), AutomaticReadPolicy.AtEnd, 100)
        } else ReadingCompletionInput.Audio(fixtureKey, AudioCompletionEvidence(s.id, s.content, document.logicalRevision, 1, 0, true),
            AudioCompletionContext(s.id, s.content, document, 1, true, true), 100)
        return ReadingStateCommand(UUID.randomUUID().toString(), s.binding, input, s, sequence)
    }
    private fun rejected(result: ReadingStateCommit, why: ReadingStateRejection) = assertEquals(ReadingStateCommit.NotApplied(why), result)
    private fun rows(directory: Path, table: String): Long = DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use {
        it.query("SELECT count(*) FROM $table") { r -> r.getLong(1) }.single()
    }

    @Test fun unknownInitialStateExplicitIntentsAndReplayPreserveLegacyBodyPositionAndReceipts() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { library ->
            library.seed(); library.download(fixtureKey, prose()); library.position(fixtureKey, 0, 17); library.flush()
            val before = library.book(fixtureKey.novel); val bytes = DesktopSavedChapterDao(library).readSnapshot(fixtureKey)
            val download = DesktopDownloadDao(library).currentReceipt(fixtureKey); val dao = DesktopReadingStateDao(library)
            assertEquals(ReadStatus.UNKNOWN, dao.read(fixtureKey)!!.status); assertEquals(0L, rows(directory, "reading_states"))
            val command = explicit(prepare(dao), true); assertFalse((dao.commit(command) as ReadingStateCommit.Committed).replayed)
            assertEquals(2L, (dao.commit(explicit(prepare(dao), true)) as ReadingStateCommit.Committed).intentRevision)
            dao.commit(explicit(prepare(dao), false))
            val replay = dao.commit(command) as ReadingStateCommit.Committed
            assertTrue(replay.replayed); assertEquals(ReadStatus.READ, replay.valueAtCommit.status); assertEquals(ReadStatus.UNREAD, replay.current.status)
            assertEquals(100L, replay.current.firstReadAt); assertEquals(3L, replay.intentRevision)
            rejected(dao.commit(command.copy(input = ReadingCompletionInput.MarkRead(fixtureKey, 101))), ReadingStateRejection.CONFLICT)
            assertEquals(before, library.book(fixtureKey.novel)); assertEquals(bytes, DesktopSavedChapterDao(library).readSnapshot(fixtureKey))
            assertEquals(download, DesktopDownloadDao(library).currentReceipt(fixtureKey)); assertNull(dao.prepare(fixtureKey.copy(url = "/missing")))
            val other = fixtureKey.copy(url = "/second")
            library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters + io.grimoire.api.model.novel.Chapter(other.url, "Second"))
            val second = dao.prepare(other)!!
            dao.commit(ReadingStateCommand("other", second.binding, ReadingCompletionInput.MarkRead(other, 200), null, 0))
            assertEquals(200L, dao.read(other)!!.firstReadAt); assertEquals(ReadStatus.UNREAD, dao.read(fixtureKey)!!.status)
            assertEquals(100L, dao.read(fixtureKey)!!.firstReadAt)
        }
    }

    @Test fun queuedVisualAndAudioCannotUndoUnreadWhileFreshBindingCanAndNoOpsStoreNoReceipt() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library)
            for (kind in ReadingStateWriterKind.entries) {
                dao.commit(explicit(prepare(dao), false)); val s = session(library, kind); val old = automatic(s)
                assertTrue(dao.commit(old) is ReadingStateCommit.Committed)
                dao.commit(explicit(prepare(dao), false))
                rejected(dao.commit(automatic(s, 2)), ReadingStateRejection.STALE)
                assertEquals(ReadStatus.UNREAD, (dao.commit(old) as ReadingStateCommit.Committed).current.status)
                val fresh = session(library, kind); assertTrue(dao.commit(automatic(fresh)) is ReadingStateCommit.Committed)
                val count = rows(directory, "reading_state_receipts")
                repeat(10) { rejected(dao.commit(automatic(fresh, 2)), ReadingStateRejection.NO_CHANGE) }
                assertEquals(count, rows(directory, "reading_state_receipts"))
            }
            assertEquals(100L, dao.read(fixtureKey)!!.firstReadAt)
        }
    }

    @Test fun contentAbaAndRelockRevokeAutomaticAuthorityButRemovalRetiresTheIncarnation() = runBlocking<Unit> {
        LocalLibrary(temporary.newFolder().toPath()).use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library)
            val old = session(library, ReadingStateWriterKind.AUDIO); val committed = explicit(prepare(dao), true); dao.commit(committed)
            library.download(fixtureKey, prose("Different")); library.download(fixtureKey, prose())
            rejected(dao.commit(automatic(old)), ReadingStateRejection.STALE)
            library.remember(fixtureKey.novel, fixtureNovel, fixtureChapters.map { it.copy(locked = true) })
            assertEquals(ReadStatus.READ, dao.read(fixtureKey)!!.status)
            assertNull(dao.register(prepare(dao).binding, old.content, document, old.kind))
            assertTrue(dao.commit(explicit(prepare(dao), false)) is ReadingStateCommit.Committed)
            library.seed(); assertEquals(ReadStatus.UNREAD, dao.read(fixtureKey)!!.status)
            library.remember(fixtureKey.novel, fixtureNovel, emptyList()); assertNull(dao.read(fixtureKey)); library.seed()
            rejected(dao.commit(committed), ReadingStateRejection.STALE)
            val fresh = prepare(dao); assertNotEquals(old.binding.incarnation, fresh.binding.incarnation); assertEquals(ReadStatus.UNKNOWN, fresh.value.status)
            assertTrue(dao.commit(explicit(fresh, true)) is ReadingStateCommit.Committed)
        }
    }

    @Test fun transactionFaultAndLostReplyKeepStateRevisionAndReceiptAtomic() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath(); var fault: StorageCheckpoint? = null
        LocalLibrary(directory) { if (it == fault) throw IOException("State fault") }.use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); val p = prepare(dao); val command = explicit(p, true)
            fault = StorageCheckpoint.BEFORE_COMMIT
            try { dao.commit(command); fail("Must roll back") } catch (e: IOException) { assertEquals("State fault", e.message) }
            fault = null; assertEquals(p, prepare(dao)); assertEquals(0L, rows(directory, "reading_state_receipts"))
            fault = StorageCheckpoint.AFTER_COMMIT
            try { dao.commit(command); fail("Must lose reply") } catch (e: IOException) { assertEquals("State fault", e.message) }
            fault = null; val replay = dao.commit(command) as ReadingStateCommit.Committed
            assertTrue(replay.replayed); assertEquals(1L, replay.intentRevision); assertEquals(100L, replay.current.firstReadAt)
            assertEquals(1L, rows(directory, "reading_state_receipts"))
        }
    }

    @Test fun cancellationBeforeCommitRollsBackAllThreePersistedValues() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath(); val entered = CountDownLatch(1); val release = CountDownLatch(1); var armed = false
        LocalLibrary(directory) {
            if (armed && it == StorageCheckpoint.BEFORE_COMMIT) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        }.use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); val p = prepare(dao); val command = explicit(p, true)
            armed = true; val job = launch(Dispatchers.Default) { dao.commit(command) }
            try { check(entered.await(5, TimeUnit.SECONDS)); job.cancel() } finally { armed = false; release.countDown() }
            job.join(); assertTrue(job.isCancelled); assertEquals(p, prepare(dao)); assertEquals(0L, rows(directory, "reading_state_receipts"))
            assertTrue(dao.commit(command) is ReadingStateCommit.Committed)
        }
    }

    @Test fun reopenAcknowledgesKnownCommitButRejectsUncommittedOldScopeAndOldIntent() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        val (committed, uncommitted) = LocalLibrary(directory).use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); val initial = prepare(dao); val committed = explicit(initial, true)
            dao.commit(committed); rejected(dao.commit(explicit(initial, false)), ReadingStateRejection.CONFLICT)
            dao.commit(explicit(prepare(dao), false))
            committed to explicit(prepare(dao), true)
        }
        LocalLibrary(directory).use { reopened ->
            val next = DesktopReadingStateDao(reopened); val replay = next.commit(committed) as ReadingStateCommit.Committed
            assertTrue(replay.replayed); assertEquals(ReadStatus.UNREAD, replay.current.status)
            rejected(next.commit(uncommitted), ReadingStateRejection.STALE)
            assertTrue(next.commit(explicit(prepare(next), true)) is ReadingStateCommit.Committed)
        }
    }

    @Test fun corruptProtocolFailsClosedWithoutChangingBodyOrOverwritingMetadata() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); prepare(dao); val before = library.book(fixtureKey.novel)
            mutateDatabase(directory, "UPDATE reading_states SET state=replace(state,'\"format\":1','\"format\":99')")
            try { dao.prepare(fixtureKey); fail("Corrupt must not become absent") } catch (_: IllegalArgumentException) { }
            assertEquals(before, library.book(fixtureKey.novel)); assertEquals(0L, rows(directory, "reading_state_receipts"))
            DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use {
                assertTrue(it.query("SELECT state FROM reading_states") { r -> r.getString(1) }.single().contains("\"format\":99"))
            }
        }
    }

    @Test fun exactWriterRetirementPreservesOtherKindValuesReceiptsAndLegacyData() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { library ->
            library.seed(); library.download(fixtureKey, prose()); val before = library.book(fixtureKey.novel)
            val body = DesktopSavedChapterDao(library).readSnapshot(fixtureKey); val dao = DesktopReadingStateDao(library)
            val visual = session(library, ReadingStateWriterKind.VISUAL); val audio = session(library, ReadingStateWriterKind.AUDIO)
            val accepted = automatic(visual); dao.commit(accepted); val value = dao.read(fixtureKey)
            assertTrue(dao.retire(visual)); assertFalse(dao.retire(visual))
            rejected(dao.commit(automatic(visual, 2)), ReadingStateRejection.STALE)
            rejected(dao.commit(automatic(audio)), ReadingStateRejection.NO_CHANGE)
            assertTrue((dao.commit(accepted) as ReadingStateCommit.Committed).replayed)
            val newer = session(library, ReadingStateWriterKind.VISUAL)
            assertFalse(dao.retire(visual)); rejected(dao.commit(automatic(newer)), ReadingStateRejection.NO_CHANGE)
            assertEquals(1L, rows(directory, "reading_state_receipts")); assertEquals(value, dao.read(fixtureKey))
            assertEquals(before, library.book(fixtureKey.novel)); assertEquals(body, DesktopSavedChapterDao(library).readSnapshot(fixtureKey))
            library.remember(fixtureKey.novel, fixtureNovel, emptyList()); library.seed()
            assertFalse(dao.retire(newer)); assertEquals(ReadStatus.UNKNOWN, dao.read(fixtureKey)!!.status)
            assertEquals(0L, rows(directory, "reading_states"))
        }
    }

    @Test fun retirementFaultRollsBackAndReopenRefusesTheOldOwner() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath(); var fault = false; lateinit var old: ReadingStateSession
        LocalLibrary(directory) { if (fault && it == StorageCheckpoint.BEFORE_COMMIT) throw IOException("Retire fault") }.use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); old = session(library, ReadingStateWriterKind.AUDIO)
            fault = true
            try { dao.retire(old); fail("Must roll back") } catch (e: IOException) { assertEquals("Retire fault", e.message) }
            fault = false; assertTrue(dao.commit(automatic(old)) is ReadingStateCommit.Committed)
        }
        LocalLibrary(directory).use { library ->
            val dao = DesktopReadingStateDao(library); assertFalse(dao.retire(old))
            val fresh = session(library, ReadingStateWriterKind.AUDIO); assertTrue(dao.retire(fresh))
            assertEquals(100L, dao.read(fixtureKey)!!.firstReadAt)
            mutateDatabase(directory, "UPDATE reading_states SET state=replace(state,'\"format\":1','\"format\":99')")
            try { dao.retire(fresh); fail("Corrupt cannot become a no-op") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun cancellationBeforeRetirementCommitLeavesTheWriterUsable() = runBlocking<Unit> {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); var armed = false
        LocalLibrary(temporary.newFolder().toPath()) {
            if (armed && it == StorageCheckpoint.BEFORE_COMMIT) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        }.use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); val s = session(library, ReadingStateWriterKind.VISUAL)
            armed = true; val pending = launch(Dispatchers.Default) { dao.retire(s) }
            try { check(entered.await(5, TimeUnit.SECONDS)); pending.cancel() } finally { armed = false; release.countDown() }
            pending.join(); assertTrue(pending.isCancelled)
            assertTrue(dao.commit(automatic(s)) is ReadingStateCommit.Committed)
        }
    }

    @Test fun sharedFacadeConfirmsLostReplyAndReconcilesNewerUnread() = runBlocking<Unit> {
        var fault = false
        LocalLibrary(temporary.newFolder().toPath()) { if (fault && it == StorageCheckpoint.AFTER_COMMIT) { fault = false; throw IOException("Lost reply") } }.use { library ->
            library.seed(); val dao = DesktopReadingStateDao(library); val facade = ReadingStateFacade(dao, null)
            val command = facade.prepareExplicit(ReadingCompletionInput.MarkRead(fixtureKey, 100), "read")!!
            fault = true
            try { facade.submit(command); fail("Lost reply expected") } catch (e: IOException) { assertEquals("Lost reply", e.message) }
            facade.submit(facade.prepareExplicit(ReadingCompletionInput.MarkUnread(fixtureKey), "unread")!!)
            val result = facade.submit(command)
            assertTrue((result.confirmation as ReadingStateConfirmation.Confirmed).replayed)
            assertEquals(ReadStatus.READ, (result.confirmation as ReadingStateConfirmation.Confirmed).valueAtCommit.status); assertEquals(ReadStatus.UNREAD, result.current!!.status)
            assertEquals(100L, result.current!!.firstReadAt); assertEquals(ReadingWordCountOutcome.NotApplicable, result.wordCount)
        }
    }

    @Test fun sql2MigrationRollsBackAsAUnitAndPreservesEveryExistingTableAndRow() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        Files.writeString(directory.resolve("library.json"), """{"schema":2,"storage":"sqlite","database":"library.sqlite","id":"frozen-sql2"}""")
        val checkpoint = ReadingCheckpointState(ReadingSessionBinding("frozen", ChapterStorageObservation(fixtureKey, "frozen", "frozen-generation", ChapterContentPresence.SAVED), document.logicalRevision), 1,
            ReadingAnchor(fixtureKey, document.logicalRevision, AnchorPosition.End))
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { db ->
            val schema = javaClass.getResourceAsStream("/reading-state/sql2.sql")!!.bufferedReader().use { it.readText() }
            schema.lineSequence().filter { it.isNotBlank() && !it.startsWith("--") }.forEach { db.execute(it) }
            db.execute("INSERT INTO reading_checkpoints VALUES(17,'/novel','/chapter',?)", encodeReadingCheckpoint(checkpoint))
        }
        fun snapshot() = DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { db ->
            val definitions = db.query("SELECT name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT IN ('reading_states','reading_state_receipts','opened_history') ORDER BY name") { it.getString(1) to it.getString(2) }
            definitions.associate { (name, sql) -> name to (sql to db.query("SELECT * FROM $name ORDER BY 1,2") { row -> (1..row.metaData.columnCount).map { row.getObject(it) } }) }
        }
        val before = snapshot()
        try { LocalLibrary(directory) { if (it == StorageCheckpoint.BEFORE_COMMIT) throw IOException("Migration fault") }; fail("Must roll back") }
        catch (e: IOException) { assertEquals("Migration fault", e.message) }
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { db ->
            assertEquals(2, db.query("PRAGMA user_version") { it.getInt(1) }.single())
            assertEquals(0, db.query("SELECT count(*) FROM sqlite_master WHERE name='reading_states'") { it.getInt(1) }.single())
        }
        assertEquals(before, snapshot())
        LocalLibrary(directory).use { library ->
            val dao = DesktopReadingStateDao(library); assertEquals(ReadStatus.UNKNOWN, dao.read(fixtureKey)!!.status)
            assertEquals(0L, rows(directory, "reading_states")); assertEquals(before, snapshot()); prepare(dao); assertEquals(before, snapshot())
            assertEquals(StoredChapterContent("Legacy prose\u001c<i>Legacy prose</i>", 1), DesktopSavedChapterDao(library).readSnapshot(fixtureKey)!!.content)
            assertEquals("frozen-receipt", DesktopDownloadDao(library).currentReceipt(fixtureKey)!!.attemptId)
            assertEquals(checkpoint.anchor, DesktopReadingCheckpointDao(library).read(fixtureKey))
        }
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { assertEquals(4, it.query("PRAGMA user_version") { r -> r.getInt(1) }.single()) }
    }
}
