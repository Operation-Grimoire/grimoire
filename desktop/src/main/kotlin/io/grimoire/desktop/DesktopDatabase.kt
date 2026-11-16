package io.grimoire.desktop

import io.grimoire.reading.*
import io.grimoire.reading.storage.downloadDigest
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

internal enum class StorageCheckpoint { BEFORE_COMMIT, AFTER_COMMIT, BEFORE_PUBLISH, BEFORE_BACKUP_PUBLISH, BEFORE_HANDOFF, AFTER_HANDOFF }

/** One local desktop process owns the store. Every caller is serialized by LocalLibrary. */
internal class DesktopDatabase(
    private val directory: Path,
    private val checkpoint: (StorageCheckpoint) -> Unit,
) : Closeable {
    private val leaseChannel: FileChannel
    private val lease: java.nio.channels.FileLock
    val connection: Connection
    private val marker = directory.resolve("library.json")
    private val database = directory.resolve("library.sqlite")
    private val id: String
    private var closed = false

    init {
        Files.createDirectories(directory)
        leaseChannel = FileChannel.open(directory.resolve(".library.lock"), CREATE, WRITE)
        lease = try {
            leaseChannel.tryLock() ?: throw IOException("Desktop library is already open in another process")
        } catch (failure: Exception) {
            leaseChannel.close()
            if (failure is OverlappingFileLockException) throw IOException("Desktop library is already open", failure)
            throw failure
        }
        var opened: Connection? = null
        try {
            Class.forName("org.sqlite.JDBC")
            if (!Files.exists(database)) importLegacy()
            opened = DriverManager.getConnection("jdbc:sqlite:$database")
            connection = opened
            require(connection.scalar("PRAGMA application_id") == APPLICATION_ID.toLong() &&
                connection.scalar("PRAGMA user_version") in 1L..2L) { "Unsupported desktop database" }
            id = connection.query("SELECT value FROM settings WHERE name='id'") { it.getString(1) }.single()
            val phase = connection.query("SELECT value FROM settings WHERE name='phase'") { it.getString(1) }.single()
            require(phase == "active" || phase == "imported") { "Invalid desktop migration phase" }
            if (phase == "imported") finishHandoff()
            verifyMarker()
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys=ON")
                statement.execute("PRAGMA busy_timeout=5000")
                statement.executeQuery("PRAGMA journal_mode=WAL").use { require(it.next() && it.getString(1) == "wal") }
                statement.execute("PRAGMA synchronous=FULL")
            }
            require(connection.scalar("PRAGMA foreign_keys") == 1L && connection.scalar("PRAGMA synchronous") == 2L)
            if (connection.scalar("PRAGMA user_version") == 1L) transaction {
                connection.execute(CREATE_READING_CHECKPOINTS)
                connection.execute("PRAGMA user_version=2")
            }
            // The exclusive lease proves the previous process cannot still complete a callback.
            connection.execute("UPDATE contents SET active_attempt=NULL, active_owner=NULL, base_revision=NULL")
        } catch (failure: Throwable) {
            try { opened?.close() } catch (closing: Throwable) { failure.addSuppressed(closing) }
            lease.release()
            leaseChannel.close()
            throw failure
        }
    }

    fun verifyMarker() {
        check(!closed) { "Desktop library is closed" }
        // Old versions do not honor the lease. Refuse to overwrite their manifest if one is still running.
        val root = JSONObject(Files.readString(marker))
        if (root.opt("schema") != 2 || root.optString("storage") != "sqlite" ||
            root.optString("database") != "library.sqlite" || root.optString("id") != id) {
            throw IOException("Desktop storage handoff changed; close older readers and restore a coherent store copy")
        }
    }

    fun <T> transaction(beforeCommit: () -> Unit = {}, action: () -> T): T {
        verifyMarker()
        connection.autoCommit = false
        var committed = false
        var primary: Throwable? = null
        try {
            val result = action()
            checkpoint(StorageCheckpoint.BEFORE_COMMIT)
            beforeCommit()
            connection.commit()
            committed = true
            checkpoint(StorageCheckpoint.AFTER_COMMIT)
            return result
        } catch (failure: Throwable) {
            primary = failure
            if (!committed) try { connection.rollback() } catch (rollback: Throwable) {
                failure.addSuppressed(rollback)
                // Never switch autoCommit on after a failed rollback: it could commit unknown state.
                try { close() } catch (closing: Throwable) { failure.addSuppressed(closing) }
            }
            throw failure
        } finally {
            if (!connection.isClosed) try { connection.autoCommit = true } catch (failure: Throwable) {
                if (primary != null) primary.addSuppressed(failure) else throw failure
            }
        }
    }

    private fun importLegacy() {
        val manifest = if (Files.exists(marker)) Files.readAllBytes(marker) else null
        val root = manifest?.let { JSONObject(String(it, Charsets.UTF_8)) }
        require(root == null || root.opt("schema") == 1) { "Unsupported desktop library format" }
        val records = root?.getJSONArray("books") ?: JSONArray()
        val books = (0 until records.length()).map { decodeBook(records.getJSONObject(it)) }
        require(books.map { it.key }.distinct().size == books.size) { "Duplicate desktop novel identity" }
        require(books.all { book -> book.chapters.map { it.url }.distinct().size == book.chapters.size }) { "Duplicate desktop chapter identity" }
        val fingerprints = JSONObject().put("library.json", manifest?.let(::sha256) ?: JSONObject.NULL)
        val staging = Files.createTempFile(directory, ".library-import-", ".sqlite")
        try {
            DriverManager.getConnection("jdbc:sqlite:$staging").use { db ->
                db.createStatement().use { s ->
                    s.execute("PRAGMA synchronous=FULL")
                    s.execute("PRAGMA application_id=$APPLICATION_ID")
                    s.execute("PRAGMA user_version=2")
                }
                db.autoCommit = false
                db.execute("CREATE TABLE settings(name TEXT PRIMARY KEY, value TEXT NOT NULL)")
                db.execute("CREATE TABLE books(source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, metadata TEXT NOT NULL, PRIMARY KEY(source_id,novel_url))")
                db.execute("""CREATE TABLE contents(
                    source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL,
                    has_content INTEGER NOT NULL DEFAULT 0 CHECK(has_content IN (0,1)),
                    content TEXT, content_version INTEGER, digest TEXT, revision TEXT NOT NULL,
                    word_count INTEGER NOT NULL DEFAULT 0 CHECK(word_count>=0), receipt_attempt TEXT,
                    active_attempt TEXT, active_owner TEXT, base_revision TEXT,
                    PRIMARY KEY(source_id,novel_url,chapter_url),
                    FOREIGN KEY(source_id,novel_url) REFERENCES books(source_id,novel_url) ON DELETE CASCADE)""")
                db.execute(CREATE_READING_CHECKPOINTS)
                db.execute("INSERT INTO settings VALUES('id',?),('phase','imported')", UUID.randomUUID().toString())
                books.forEach { book -> db.execute("INSERT INTO books VALUES(?,?,?)", book.key.sourceId, book.key.url,
                    encodeBook(book).apply { remove("downloaded") }.toString()) }
                // Keep only one chapter payload alive, even for libraries larger than the JVM heap.
                books.forEach { book -> book.downloaded.forEach { url ->
                    val key = ChapterKey(book.key, url)
                    val file = legacyContentFile(directory, key)
                    val bytes = Files.readAllBytes(file)
                    fingerprints.put(file.fileName.toString(), sha256(bytes))
                    val body = legacyContent(key, bytes)
                    db.execute("""INSERT INTO contents
                    (source_id,novel_url,chapter_url,has_content,content,content_version,digest,revision,word_count)
                    VALUES(?,?,?,1,?,?,?,?,?)""", key.novel.sourceId, key.novel.url, key.url,
                    body.content, body.version, downloadDigest(body), UUID.randomUUID().toString(),
                    chapterWordCount(decodeChapterContent(body)))
                } }
                db.execute("INSERT INTO settings VALUES('legacy',?)", fingerprints.toString())
                db.commit()
            }
            verifyLegacy(fingerprints)
            checkpoint(StorageCheckpoint.BEFORE_PUBLISH)
            verifyLegacy(fingerprints)
            // There is no fallback to non-atomic replacement on unsupported filesystems.
            Files.move(staging, database, ATOMIC_MOVE)
        } finally { Files.deleteIfExists(staging) }
    }

    private fun finishHandoff() {
        val current = if (Files.exists(marker)) JSONObject(Files.readString(marker)) else null
        if (current?.opt("schema") == 2) {
            verifyMarker()
        } else {
            val expected = JSONObject(connection.query("SELECT value FROM settings WHERE name='legacy'") { it.getString(1) }.single())
            verifyLegacy(expected)
            val backup = directory.resolve("library.json.before-sqlite")
            if (!expected.isNull("library.json")) {
                if (Files.exists(backup)) require(sha256(Files.readAllBytes(backup)) == expected.getString("library.json")) { "Conflicting legacy backup" }
                else {
                    val temporary = Files.createTempFile(directory, ".library-backup-", ".tmp")
                    try {
                        Files.copy(marker, temporary, REPLACE_EXISTING)
                        FileChannel.open(temporary, WRITE).use { it.force(true) }
                        checkpoint(StorageCheckpoint.BEFORE_BACKUP_PUBLISH)
                        verifyLegacy(expected)
                        require(sha256(Files.readAllBytes(temporary)) == expected.getString("library.json")) { "Legacy backup copy changed" }
                        Files.move(temporary, backup, ATOMIC_MOVE)
                    } finally { Files.deleteIfExists(temporary) }
                }
            }
            checkpoint(StorageCheckpoint.BEFORE_HANDOFF)
            verifyLegacy(expected)
            val staging = Files.createTempFile(directory, ".library-handoff-", ".tmp")
            try {
                Files.writeString(staging, JSONObject().put("schema", 2).put("storage", "sqlite")
                    .put("database", "library.sqlite").put("id", id).toString())
                FileChannel.open(staging, WRITE).use { it.force(true) }
                Files.move(staging, marker, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally { Files.deleteIfExists(staging) }
            checkpoint(StorageCheckpoint.AFTER_HANDOFF)
        }
        connection.execute("UPDATE settings SET value='active' WHERE name='phase'")
    }

    private fun verifyLegacy(expected: JSONObject) {
        for (name in expected.keySet()) {
            require(name == "library.json" || name.matches(Regex("chapter-[a-f0-9]{64}\\.json"))) { "Invalid legacy fingerprint identity" }
            require(!expected.isNull(name) || name == "library.json") { "Invalid legacy fingerprint" }
            val path = directory.resolve(name)
            if (expected.isNull(name)) require(!Files.exists(path)) { "Legacy library changed during migration" }
            else require(sha256(Files.readAllBytes(path)) == expected.getString(name)) { "Legacy library changed during migration" }
        }
    }

    override fun close() {
        if (closed) return
        try { connection.close() } finally {
            closed = true
            try { lease.release() } finally { leaseChannel.close() }
        }
    }

    companion object { private const val APPLICATION_ID = 0x4752494D }
}

internal fun Connection.execute(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { s ->
    s.bind(args); s.executeUpdate()
}
internal fun <T> Connection.query(sql: String, vararg args: Any?, read: (ResultSet) -> T): List<T> = prepareStatement(sql).use { s ->
    s.bind(args)
    s.executeQuery().use { r -> buildList { while (r.next()) add(read(r)) } }
}
private fun PreparedStatement.bind(args: Array<out Any?>) { args.forEachIndexed { index, value -> setObject(index + 1, value) } }
private fun Connection.scalar(sql: String) = query(sql) { it.getLong(1) }.single()

internal fun legacyContentFile(directory: Path, key: ChapterKey): Path {
    val identity = JSONArray(listOf(key.novel.sourceId, key.novel.url, key.url)).toString()
    return directory.resolve("chapter-${sha256(identity.toByteArray(Charsets.UTF_8))}.json")
}

private fun legacyContent(key: ChapterKey, bytes: ByteArray): StoredChapterContent {
    val root = JSONObject(String(bytes, Charsets.UTF_8))
    val schema = root.opt("schema") as? Int ?: error("Invalid desktop chapter format")
    require(schema in 1..2) { "Unsupported desktop chapter format" }
    val version = if (schema == 1) 1 else root.opt("contentVersion") as? Int
        ?: throw IllegalArgumentException("Invalid desktop chapter content version")
    require(schema != 2 || version == 2) { "Unsupported desktop chapter content version" }
    require(schema != 1 || !root.has("contentVersion")) { "Legacy chapter cannot declare a different content format" }
    require(root.getLong("sourceId") == key.novel.sourceId && root.getString("novelUrl") == key.novel.url && root.getString("chapterUrl") == key.url) { "Downloaded chapter identity mismatch" }
    val content = root.getString("content")
    require(root.getString("sha256") == sha256(content.toByteArray(Charsets.UTF_8))) { "Downloaded chapter is corrupt" }
    return StoredChapterContent(content, version).also { decodeChapterContent(it) }
}

/** SQL2 expansion; SQL1 readers refuse the newer user_version before touching the store. */
private const val CREATE_READING_CHECKPOINTS = """CREATE TABLE reading_checkpoints(
    source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, state TEXT NOT NULL,
    PRIMARY KEY(source_id,novel_url,chapter_url),
    FOREIGN KEY(source_id,novel_url,chapter_url) REFERENCES contents(source_id,novel_url,chapter_url) ON DELETE CASCADE)"""
