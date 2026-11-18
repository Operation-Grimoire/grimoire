package io.grimoire.desktop

import io.grimoire.reading.storage.OpenedHistoryWrites
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicBoolean

class OpenedHistoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val chapter = DesktopOpenedChapter(fixtureKey, "A public novel", "First")

    @Test fun sql3UpgradeRollsBackAndPreservesExistingTablesAndRows() {
        val directory = temporary.newFolder().toPath()
        val database = directory.resolve("library.sqlite")
        Files.writeString(directory.resolve("library.json"),
            """{"schema":2,"storage":"sqlite","database":"library.sqlite","id":"frozen-sql2"}""")
        DriverManager.getConnection("jdbc:sqlite:$database").use { db ->
            for (name in listOf("sql2.sql", "sql3-history-base.sql")) {
                javaClass.getResourceAsStream("/reading-state/$name")!!.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith("--") }.forEach { db.execute(it) }
                }
            }
        }
        val before = existingTables(database)
        try {
            LocalLibrary(directory) { if (it == StorageCheckpoint.BEFORE_COMMIT) throw IOException("History migration failure") }.close()
            fail("Migration should roll back")
        } catch (failure: IOException) {
            assertEquals("History migration failure", failure.message)
        }
        assertEquals(3L, schemaVersion(database))
        assertEquals(0L, historyTables(database))
        assertEquals(before, existingTables(database))
        LocalLibrary(directory).use { library ->
            assertFalse(library.historyRecording())
            assertTrue(library.openedHistory().isEmpty())
            assertEquals(ReadPosition("/chapter", 0, 17), library.saved().single().position)
            assertEquals(before, existingTables(database))
        }
        assertEquals(4L, schemaVersion(database))
        assertEquals(1L, historyTables(database))
    }

    @Test fun stableReopenIdAndOrderedDeletionAndClearPreserveLibrary() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            LocalLibrary(directory).use { library ->
                library.seed()
                library.download(fixtureKey, prose())
                library.selectResumeChapter(fixtureKey)
                library.position(fixtureKey, 0, 14)
                library.flush()
                val originalBook = library.book(fixtureKey.novel)
                val writes = OpenedHistoryWrites(scope, DesktopOpenedHistoryDao(library))
                writes.open(false, 100) { chapter }!!.await().getOrThrow()
                val first = library.openedHistory().single()
                writes.open(false, 200) { chapter.copy(chapterTitle = "Updated") }!!.await().getOrThrow()
                assertEquals(first.id, library.openedHistory().single().id)
                assertEquals("Updated", library.openedHistory().single().chapter.chapterTitle)
                assertNull(writes.open(true, 300) { error("Private opens must not resolve a snapshot") })

                val resolve = CompletableDeferred<DesktopOpenedChapter>()
                val prior = writes.open(false, 400) { resolve.await() }!!
                val delete = writes.delete(listOf(first.id))!!
                val clear = writes.clear()
                val later = writes.open(false, 500) { chapter }!!
                resolve.complete(chapter)
                for (action in listOf(prior, delete, clear, later)) action.await().getOrThrow()
                val remaining = library.openedHistory().single()
                assertNotEquals(first.id, remaining.id)
                assertEquals(500L, remaining.openedAtMillis)
                assertEquals(originalBook, library.book(fixtureKey.novel))
                assertEquals(prose(), library.downloaded(fixtureKey))
            }
        } finally {
            scope.cancel()
        }
        LocalLibrary(directory).use { assertEquals(chapter, it.openedHistory().single().chapter) }
    }

    @Test fun modelRecordsFailedLoadsAfterOptInAndDrainsOnClose() = runBlocking<Unit> {
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory).apply { seed() }
        val book = library.book(fixtureKey.novel)!!
        val model = desktopReaderModel(library, "1.6.0", AtomicBoolean(false))
        suspend fun awaitIdle() { withTimeout(10_000) { model.state.first { !it.busy } } }
        try {
            assertFalse(model.state.value.historyRecording)
            model.openSaved(book)
            awaitIdle()
            model.read(book.chapters.first())
            awaitIdle()
            assertNotNull(model.state.value.error)
            assertTrue(library.openedHistory().isEmpty())

            model.setHistoryRecording(true)
            awaitIdle()
            assertTrue(model.state.value.historyRecording)
            model.read(book.chapters.first())
            awaitIdle()
            assertNotNull(model.state.value.error)
        } finally {
            model.close()
        }
        LocalLibrary(directory).use { reopened ->
            assertTrue(reopened.historyRecording())
            assertEquals(fixtureKey, reopened.openedHistory().single().chapter.key)
            assertEquals(book.resumeChapterUrl, reopened.book(book.key)!!.resumeChapterUrl)
            assertEquals(book.position, reopened.book(book.key)!!.position)
        }
    }

    private fun existingTables(database: Path) = DriverManager.getConnection("jdbc:sqlite:$database").use { db ->
        val definitions = db.query("""SELECT name,sql FROM sqlite_master WHERE type='table'
            AND name NOT LIKE 'sqlite_%' AND name!='opened_history' ORDER BY name""") { it.getString(1) to it.getString(2) }
        definitions.associate { (name, sql) ->
            name to (sql to db.query("SELECT * FROM $name ORDER BY 1,2") { row ->
                (1..row.metaData.columnCount).map(row::getObject)
            })
        }
    }

    private fun schemaVersion(database: Path) = DriverManager.getConnection("jdbc:sqlite:$database").use { db ->
        db.query("PRAGMA user_version") { it.getLong(1) }.single()
    }

    private fun historyTables(database: Path) = DriverManager.getConnection("jdbc:sqlite:$database").use { db ->
        db.query("SELECT count(*) FROM sqlite_master WHERE name='opened_history'") { it.getLong(1) }.single()
    }
}
