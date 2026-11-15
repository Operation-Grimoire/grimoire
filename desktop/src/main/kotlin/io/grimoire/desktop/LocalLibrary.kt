package io.grimoire.desktop

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.nio.file.Path
import java.util.UUID
import java.sql.ResultSet

data class ReadPosition(val chapterUrl: String, val paragraph: Int, val offset: Int)
data class SavedBook(
    val key: NovelKey,
    val novel: Novel,
    val chapters: List<Chapter>,
    val favorite: Boolean = false,
    val downloaded: Set<String> = emptySet(),
    val position: ReadPosition? = null,
)

/** Desktop facade; SQL owns committed state, pending positions stay off the UI's disk path. */
class LocalLibrary internal constructor(directory: Path, checkpoint: (StorageCheckpoint) -> Unit) : Closeable {
    constructor(directory: Path) : this(directory, {})
    private val database = DesktopDatabase(directory, checkpoint)
    private val db get() = database.connection
    private val owner = UUID.randomUUID().toString()
    private val pendingPositions = linkedMapOf<NovelKey, ReadPosition>()
    private var positionBooks = emptyMap<NovelKey, SavedBook>()
    private var closed = false

    init {
        try { positionBooks = readBooks().associateBy { it.key } }
        catch (failure: Throwable) { database.close(); throw failure }
    }

    private fun readBooks(): List<SavedBook> {
        database.verifyMarker()
        return db.query("SELECT source_id,novel_url,metadata FROM books ORDER BY rowid") { row ->
            readBook(row)
        }
    }
    private fun readBook(row: ResultSet): SavedBook {
        val key = NovelKey(row.getLong(1), row.getString(2))
        val downloaded = db.query("SELECT chapter_url FROM contents WHERE source_id=? AND novel_url=? AND has_content=1",
            key.sourceId, key.url) { it.getString(1) }
        return decodeBook(JSONObject(row.getString(3)).put("downloaded", JSONArray(downloaded))).also {
            require(it.key == key) { "Desktop novel identity mismatch" }
        }.let { it.copy(position = pendingPositions[key] ?: it.position) }
    }
    private fun refresh(): List<SavedBook> = readBooks().also { positionBooks = it.associateBy { b -> b.key } }
    private fun <T> write(beforeCommit: () -> Unit = {}, action: () -> T): T = try {
        database.transaction(beforeCommit, action)
    } catch (failure: Throwable) {
        // A failed reply can follow a successful commit. Scroll callbacks must not use old eligibility.
        positionBooks = emptyMap()
        throw failure
    }
    @Synchronized fun saved(): List<SavedBook> = refresh().filter { it.favorite }
    @Synchronized fun book(key: NovelKey): SavedBook? {
        database.verifyMarker()
        val current = db.query("SELECT source_id,novel_url,metadata FROM books WHERE source_id=? AND novel_url=?",
            key.sourceId, key.url, read = ::readBook).singleOrNull()
        positionBooks = if (current == null) positionBooks - key else positionBooks + (key to current)
        return current
    }

    @Synchronized fun remember(key: NovelKey, novel: Novel, chapters: List<Chapter>): SavedBook {
        require(novel.url == key.url && chapters.map { it.url }.distinct().size == chapters.size)
        write {
            val old = book(key)
            val metadata = SavedBook(key, novel, chapters, old?.favorite ?: false, position = old?.position)
            db.execute("INSERT INTO books VALUES(?,?,?) ON CONFLICT(source_id,novel_url) DO UPDATE SET metadata=excluded.metadata",
                key.sourceId, key.url, encodeBook(metadata).apply { remove("downloaded") }.toString())
            // Removal/relock revokes both live attempts and replay receipts, without discarding old content.
            db.query("SELECT chapter_url FROM contents WHERE source_id=? AND novel_url=?", key.sourceId, key.url) { it.getString(1) }
                .filter { url -> chapters.none { it.url == url && !it.locked } }.forEach { url ->
                    db.execute("""UPDATE contents SET active_attempt=NULL,active_owner=NULL,base_revision=NULL,
                        receipt_attempt=NULL,revision=? WHERE source_id=? AND novel_url=? AND chapter_url=?""",
                        UUID.randomUUID().toString(), key.sourceId, key.url, url)
                }
        }
        return book(key)!!
    }

    @Synchronized fun favorite(key: NovelKey, value: Boolean): SavedBook = updateMembership(key, value) ?: error("Novel is no longer stored")
    @Synchronized internal fun updateMembership(key: NovelKey, value: Boolean): SavedBook? {
        write {
            val current = book(key) ?: return@write
            db.execute("UPDATE books SET metadata=? WHERE source_id=? AND novel_url=?",
                encodeBook(current.copy(favorite = value)).apply { remove("downloaded") }.toString(), key.sourceId, key.url)
        }
        return book(key)
    }

    @Synchronized fun downloaded(key: ChapterKey): List<NovelPage>? = readSavedContent(key)?.let(::decodeChapterContent)
    @Synchronized internal fun readSavedContent(key: ChapterKey): StoredChapterContent? = readSnapshot(key)?.content
    @Synchronized internal fun readSnapshot(key: ChapterKey): SavedChapterSnapshot? {
        val book = book(key.novel) ?: return null
        val row = content(key) ?: return null
        if (!row.hasContent) return null
        require(book.chapters.any { it.url == key.url && !it.locked }) { "Chapter is unavailable or locked" }
        return SavedChapterSnapshot(row.validBody(), row.revision)
    }

    @Synchronized internal fun claim(key: ChapterKey, beforeCommit: () -> Unit): DownloadAttempt? = write(beforeCommit) {
        if (eligibility(key) != null) return@write null
        val previous = content(key)
        previous?.let { if (it.hasContent) it.validBody() }
        val revision = previous?.revision ?: UUID.randomUUID().toString()
        val attempt = DownloadAttempt(key, UUID.randomUUID().toString(), revision)
        db.execute("""INSERT INTO contents(source_id,novel_url,chapter_url,revision,active_attempt,active_owner,base_revision)
            VALUES(?,?,?,?,?,?,?) ON CONFLICT(source_id,novel_url,chapter_url) DO UPDATE SET
            active_attempt=excluded.active_attempt,active_owner=excluded.active_owner,base_revision=excluded.base_revision""",
            key.novel.sourceId, key.novel.url, key.url, revision, attempt.id, owner, revision)
        attempt
    }

    @Synchronized internal fun commit(attempt: DownloadAttempt, prepared: PreparedDownload, beforeCommit: () -> Unit): DownloadCommit = write(beforeCommit) {
        require(prepared.content.version == 2 && downloadDigest(prepared.content) == prepared.digest &&
            chapterWordCount(decodeChapterContent(prepared.content)) == prepared.wordCount) { "Invalid prepared download" }
        require(decodeChapterContent(prepared.content).any { (it.content as? PageContent.Text)?.text?.isNotBlank() == true }) { "Chapter has no readable text" }
        val key = attempt.key
        eligibility(key)?.let { return@write DownloadCommit.NotApplied(it) }
        val current = content(key) ?: return@write DownloadCommit.NotApplied(DownloadRejection.STALE)
        if (current.hasContent) current.validBody()
        if (current.receipt == attempt.id) {
            require(current.digest == prepared.digest && current.version == prepared.content.version.toLong()) { "Download retry conflicts with the committed payload" }
            return@write DownloadCommit.Committed(current.receipt(key)!!, true)
        }
        if (current.active != attempt.id || current.owner != owner || current.base != attempt.baseRevision ||
            current.revision != attempt.baseRevision) return@write DownloadCommit.NotApplied(DownloadRejection.STALE)
        val revision = UUID.randomUUID().toString()
        db.execute("""UPDATE contents SET has_content=1,content=?,content_version=?,digest=?,revision=?,word_count=?,
            receipt_attempt=?,active_attempt=NULL,active_owner=NULL,base_revision=NULL
            WHERE source_id=? AND novel_url=? AND chapter_url=?""", prepared.content.content, prepared.content.version,
            prepared.digest, revision, prepared.wordCount.takeIf { it > 0 } ?: current.wordCount,
            attempt.id, key.novel.sourceId, key.novel.url, key.url)
        DownloadCommit.Committed(content(key)!!.receipt(key)!!, false)
    }

    @Synchronized internal fun fail(attempt: DownloadAttempt, beforeCommit: () -> Unit): Boolean = write(beforeCommit) {
        val key = attempt.key
        db.execute("""UPDATE contents SET active_attempt=NULL,active_owner=NULL,base_revision=NULL WHERE
            source_id=? AND novel_url=? AND chapter_url=? AND active_attempt=? AND active_owner=? AND
            base_revision=? AND revision=?""", key.novel.sourceId, key.novel.url, key.url, attempt.id, owner,
            attempt.baseRevision, attempt.baseRevision) == 1
    }

    @Synchronized internal fun currentReceipt(key: ChapterKey): DownloadReceipt? {
        database.verifyMarker()
        return content(key)?.let { if (it.hasContent) it.validBody(); it.receipt(key) }
    }

    /** Cancellation and deletion use the same serialization boundary as commit. */
    @Synchronized internal fun deleteDownload(key: ChapterKey) = write {
        db.execute("""UPDATE contents SET has_content=0,content=NULL,content_version=NULL,digest=NULL,
            receipt_attempt=NULL,active_attempt=NULL,active_owner=NULL,base_revision=NULL,revision=?
            WHERE source_id=? AND novel_url=? AND chapter_url=?""", UUID.randomUUID().toString(), key.novel.sourceId, key.novel.url, key.url)
    }

    private fun eligibility(key: ChapterKey): DownloadRejection? {
        val book = book(key.novel) ?: return DownloadRejection.MISSING
        val chapter = book.chapters.find { it.url == key.url } ?: return DownloadRejection.MISSING
        return if (chapter.locked) DownloadRejection.LOCKED else null
    }

    /** No SQL or filesystem I/O on the scroll callback. */
    @Synchronized fun position(key: ChapterKey, paragraph: Int, offset: Int) {
        check(!closed) { "Desktop library is closed" }
        require(paragraph >= 0 && offset >= 0)
        val book = positionBooks[key.novel] ?: return
        require(book.chapters.any { it.url == key.url && !it.locked })
        pendingPositions[key.novel] = ReadPosition(key.url, paragraph, offset)
    }

    @Synchronized fun flush() {
        write {
            refresh().forEach { book ->
                if (book.key in pendingPositions) db.execute("UPDATE books SET metadata=? WHERE source_id=? AND novel_url=?",
                    encodeBook(book).apply { remove("downloaded") }.toString(), book.key.sourceId, book.key.url)
            }
        }
        pendingPositions.clear()
        refresh()
    }

    @Synchronized override fun close() {
        if (closed) return
        flush() // Preserve a usable store and pending positions if persistence fails.
        database.close()
        closed = true
    }

    private fun content(key: ChapterKey): ContentRow? = db.query("""SELECT has_content,content,content_version,digest,revision,
        word_count,receipt_attempt,active_attempt,active_owner,base_revision,typeof(content_version),typeof(word_count)
        FROM contents WHERE source_id=? AND novel_url=? AND chapter_url=?""", key.novel.sourceId, key.novel.url, key.url) { row ->
        require(row.getString(12) == "integer" && row.getLong(6) in 0..Int.MAX_VALUE.toLong()) { "Invalid desktop word count" }
        ContentRow(row.getInt(1) == 1, row.getString(2), row.getLong(3), row.getString(4), row.getString(5),
            row.getInt(6), row.getString(7), row.getString(8), row.getString(9), row.getString(10), row.getString(11))
    }.singleOrNull()

    private data class ContentRow(val hasContent: Boolean, val body: String?, val version: Long, val digest: String?,
        val revision: String, val wordCount: Int, val receipt: String?, val active: String?, val owner: String?, val base: String?, val versionType: String) {
        fun validBody(): StoredChapterContent {
            require(versionType == "integer" && version in 1..2 && body != null) { "Invalid desktop content version or missing indexed body" }
            return StoredChapterContent(body, version.toInt()).also {
                require(downloadDigest(it) == digest) { "Downloaded chapter is corrupt" }
                decodeChapterContent(it)
            }
        }
        fun receipt(key: ChapterKey): DownloadReceipt? = receipt?.let {
            require(hasContent && revision.isNotBlank()) { "Invalid desktop receipt" }
            DownloadReceipt(key, it, revision, digest!!, version.toInt(), wordCount)
        }
    }
}
