package io.grimoire.desktop

import io.grimoire.reading.storage.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** A real separate JVM, stopped only by its parent test. Never opens a personal library. */
object DesktopCrashWorker {
    @JvmStatic fun main(args: Array<String>) { runBlocking {
        val directory = Path.of(args[0])
        val mode = args[1]
        val ready = Path.of(args[2])
        var armed = mode.contains("HANDOFF") || mode == "BEFORE_PUBLISH" || mode == "BEFORE_BACKUP_PUBLISH"
        fun pause() {
            Files.writeString(ready, "ready")
            while (true) Thread.sleep(1000)
        }
        try {
            LocalLibrary(directory) { if (armed && it.name == mode) pause() }.use { store ->
                if (mode == "compete" || mode == "migrate") return@runBlocking
                if (mode == "hold") pause()
                val repository = DownloadRepository(DesktopDownloadDao(store))
                val attempt = repository.claim(fixtureKey)!!
                Files.writeString(directory.resolve("attempt.json"), JSONObject().put("id", attempt.id).put("base", attempt.baseRevision).toString())
                armed = true
                repository.commit(attempt, prose("new committed prose"))
            }
        } catch (failure: java.io.IOException) {
            if (mode == "compete" && failure.message?.contains("already open") == true) exitProcess(23)
            throw failure
        }
    } }
}

class DesktopProcessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun processDeathBeforeCommitRollsBackAndAfterCommitReplaysReceipt() = runBlocking {
        for (point in listOf(StorageCheckpoint.BEFORE_COMMIT, StorageCheckpoint.AFTER_COMMIT)) {
            val directory = temporary.newFolder("store ${point.name}").toPath()
            lateinit var original: DownloadReceipt
            LocalLibrary(directory).use { store ->
                store.seed()
                val repository = DownloadRepository(DesktopDownloadDao(store))
                original = (repository.commit(repository.claim(fixtureKey)!!, prose("old saved prose")) as DownloadCommit.Committed).receipt
                store.position(fixtureKey, 0, 31)
                store.favorite(fixtureKey.novel, true)
            }
            val ready = directory.resolve("ready")
            val child = worker(directory, point.name, ready)
            try {
                awaitReady(child, ready, directory)
                val data = JSONObject(Files.readString(directory.resolve("attempt.json")))
                val attempt = DownloadAttempt(fixtureKey, data.getString("id"), data.getString("base"))
                kill(child, directory)
                java.sql.DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { raw ->
                    val state = raw.query("SELECT has_content,receipt_attempt,active_attempt FROM contents") { Triple(it.getInt(1), it.getString(2), it.getString(3)) }.single()
                    assertEquals(1, state.first)
                    if (point == StorageCheckpoint.BEFORE_COMMIT) {
                        assertEquals(original.attemptId, state.second); assertEquals(attempt.id, state.third)
                    } else {
                        assertEquals(attempt.id, state.second); assertNull(state.third)
                    }
                }
                LocalLibrary(directory).use { store ->
                    val repository = DownloadRepository(DesktopDownloadDao(store))
                    assertEquals(ReadPosition("/chapter", 0, 31), store.saved().single().position)
                    if (point == StorageCheckpoint.BEFORE_COMMIT) {
                        assertEquals(prose("old saved prose"), store.downloaded(fixtureKey))
                        assertEquals(original, repository.currentReceipt(fixtureKey))
                        assertEquals(DownloadCommit.NotApplied(DownloadRejection.STALE), repository.commit(attempt, prose("new committed prose")))
                        assertNotNull(repository.claim(fixtureKey))
                    } else {
                        assertEquals(prose("new committed prose"), store.downloaded(fixtureKey))
                        val replay = repository.commit(attempt, prose("new committed prose")) as DownloadCommit.Committed
                        assertTrue(replay.replayed)
                        assertEquals(attempt.id, replay.receipt.attemptId)
                        assertNotEquals(original.revision, replay.receipt.revision)
                    }
                    assertEquals(setOf("/chapter"), store.book(fixtureKey.novel)!!.downloaded)
                }
            } finally { if (child.isAlive) kill(child, directory) }
        }
    }

    @Test fun processDeathBeforePublishAndDuringHandoffResumesWithoutRewritingLegacyBytes() {
        for (point in listOf(StorageCheckpoint.BEFORE_PUBLISH, StorageCheckpoint.BEFORE_BACKUP_PUBLISH, StorageCheckpoint.BEFORE_HANDOFF, StorageCheckpoint.AFTER_HANDOFF)) {
            val directory = temporary.newFolder("migration ${point.name}").toPath()
            val content = legacyFixture(directory)
            val manifest = Files.readAllBytes(directory.resolve("library.json"))
            val body = Files.readAllBytes(content)
            val ready = directory.resolve("ready")
            val child = worker(directory, point.name, ready)
            try {
                awaitReady(child, ready, directory)
                kill(child, directory)
                val orphan = if (point == StorageCheckpoint.BEFORE_PUBLISH) Files.list(directory).use { files ->
                    files.filter { it.fileName.toString().startsWith(".library-import-") }.findFirst().get()
                } else null
                val backupOrphan = if (point == StorageCheckpoint.BEFORE_BACKUP_PUBLISH) Files.list(directory).use { files ->
                    files.filter { it.fileName.toString().startsWith(".library-backup-") }.findFirst().get()
                } else null
                if (backupOrphan != null) {
                    assertFalse(Files.exists(directory.resolve("library.json.before-sqlite")))
                    // A partial temporary copy must not be adopted or treated as the final backup.
                    Files.writeString(backupOrphan, "partial copy")
                }
                val orphanBytes = orphan?.let { Files.readAllBytes(it) }
                LocalLibrary(directory).use { store ->
                    assertEquals(ReadPosition("/chapter", 0, 17), store.saved().single().position)
                    assertNotNull(store.downloaded(fixtureKey))
                    assertNull(store.currentReceipt(fixtureKey))
                }
                if (orphan != null) assertArrayEquals(orphanBytes, Files.readAllBytes(orphan))
                if (backupOrphan != null) assertEquals("partial copy", Files.readString(backupOrphan))
                assertArrayEquals(manifest, Files.readAllBytes(directory.resolve("library.json.before-sqlite")))
                assertArrayEquals(body, Files.readAllBytes(content))
            } finally { if (child.isAlive) kill(child, directory) }
        }
    }

    @Test fun competingProcessCannotWriteAndKernelReleasesLeaseAfterOwnerDeath() {
        val directory = temporary.newFolder("lease store").toPath()
        val opened = DesktopOpenedChapter(fixtureKey, "A public novel", "First")
        LocalLibrary(directory).use {
            it.seed()
            it.recordOpenedChapter(opened, 120)
        }
        val ready = directory.resolve("ready")
        val child = worker(directory, "hold", ready)
        try {
            awaitReady(child, ready, directory)
            val competitor = worker(directory, "compete", directory.resolve("competitor-ready"))
            try {
                assertTrue("Competitor did not finish", competitor.waitFor(30, TimeUnit.SECONDS))
                assertEquals("Competing process acquired the store", 23, competitor.exitValue())
            } finally { if (competitor.isAlive) kill(competitor, directory) }
            kill(child, directory)
            LocalLibrary(directory).use {
                assertNotNull(it.book(fixtureKey.novel))
                assertEquals(opened, it.openedHistory().single().chapter)
            }
        } finally { if (child.isAlive) kill(child, directory) }
    }

    @Test fun legacyLibraryLargerThanWorkerHeapImportsOneChapterAtATime() {
        val directory = temporary.newFolder("large legacy store").toPath()
        legacyFixture(directory)
        val manifest = directory.resolve("library.json")
        val root = JSONObject(Files.readString(manifest))
        val book = root.getJSONArray("books").getJSONObject(0)
        val chapters = org.json.JSONArray()
        val downloaded = org.json.JSONArray()
        // 80 MiB of legacy payloads in a worker capped at 48 MiB heap.
        val text = "x".repeat(1024 * 1024)
        val body = "$text\u001c$text"
        val digest = sha256(body.toByteArray(Charsets.UTF_8))
        for (index in 0 until 40) {
            val url = if (index == 0) "/chapter" else "/bulk$index"
            chapters.put(JSONObject().put("url", url).put("name", "Chapter $index").put("locked", false).put("number", index).put("uploadDate", 0))
            downloaded.put(url)
            val key = fixtureKey.copy(url = url)
            val record = JSONObject().put("schema", 1).put("sourceId", 17).put("novelUrl", "/novel").put("chapterUrl", url)
                .put("content", body).put("sha256", digest)
            Files.writeString(legacyContentFile(directory, key), record.toString())
        }
        book.put("chapters", chapters).put("downloaded", downloaded)
        Files.writeString(manifest, root.toString())
        val original = Files.readAllBytes(manifest)
        val child = worker(directory, "migrate", directory.resolve("ready"))
        try {
            assertTrue("Bounded-heap migration timed out", child.waitFor(90, TimeUnit.SECONDS))
            assertEquals(Files.readString(directory.resolve("worker-migrate.log")), 0, child.exitValue())
            LocalLibrary(directory).use { store ->
                assertEquals(40, store.saved().single().downloaded.size)
                assertEquals(text, (store.downloaded(fixtureKey.copy(url = "/bulk39"))!!.single().content as io.grimoire.api.model.novel.PageContent.Text).text)
            }
            assertArrayEquals(original, Files.readAllBytes(directory.resolve("library.json.before-sqlite")))
        } finally { if (child.isAlive) kill(child, directory) }
    }

    private fun worker(directory: Path, mode: String, ready: Path): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        // Java argfiles avoid the Windows command-line length limit. Quote each argument independently.
        fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val args = listOf("-Xmx48m", "-cp", System.getProperty("desktop.test.classpath"), DesktopCrashWorker::class.java.name,
            directory.toString(), mode, ready.toString())
        val file = temporary.newFile("$mode-${System.nanoTime()}.args").toPath()
        Files.writeString(file, args.joinToString("\n", transform = ::quote))
        return ProcessBuilder(java.toString(), "@$file").redirectErrorStream(true)
            .redirectOutput(directory.resolve("worker-$mode.log").toFile()).start()
    }

    private fun awaitReady(child: Process, ready: Path, directory: Path) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!Files.exists(ready) && child.isAlive && System.nanoTime() < deadline) Thread.sleep(25)
        assertTrue("Worker did not reach checkpoint; logs in $directory", Files.exists(ready))
        assertTrue(child.isAlive)
    }

    private fun kill(child: Process, directory: Path) {
        child.destroyForcibly()
        assertTrue("Owned child process did not stop", child.waitFor(15, TimeUnit.SECONDS))
        // Windows may report process completion before asynchronous handle cleanup finishes.
        // Probe the exact OS lease, never retry a storage operation or mutate the store.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            java.nio.channels.FileChannel.open(directory.resolve(".library.lock"), java.nio.file.StandardOpenOption.WRITE).use { channel ->
                val lock = channel.tryLock()
                if (lock != null) { lock.release(); return }
            }
            check(System.nanoTime() < deadline) { "OS lease was not released after owner death" }
            Thread.sleep(25)
        }
    }
}
