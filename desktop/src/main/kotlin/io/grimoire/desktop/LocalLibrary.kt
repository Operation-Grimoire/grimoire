package io.grimoire.desktop

import io.grimoire.ui.theme.ColorTheme
import io.grimoire.ui.theme.ThemeMode
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import io.grimoire.reading.state.*
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.anchor.ReadingAnchor
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.nio.file.Path
import java.util.UUID
import java.sql.Connection
import java.sql.ResultSet

data class DesktopAppearance(val themeMode: ThemeMode = ThemeMode.SYSTEM, val colorTheme: ColorTheme = ColorTheme.DEFAULT)

data class ReadPosition(val chapterUrl: String, val paragraph: Int, val offset: Int)
data class SavedBook(
    val key: NovelKey,
    val novel: Novel,
    val chapters: List<Chapter>,
    val favorite: Boolean = false,
    val downloaded: Set<String> = emptySet(),
    val position: ReadPosition? = null,
    val resumeChapterUrl: String? = null,
)

internal fun SavedBook.resumeChapter(): Chapter? =
    chapters.find { it.url == resumeChapterUrl && !it.locked }
        ?: chapters.find { it.url == position?.chapterUrl && !it.locked }

/** Desktop facade; SQL owns committed state, pending positions stay off the UI's disk path. */
class LocalLibrary internal constructor(directory: Path, checkpoint: (StorageCheckpoint) -> Unit) : Closeable {
    constructor(directory: Path) : this(directory, {})
    private val database = DesktopDatabase(directory, checkpoint)
    private val db get() = database.connection
    private val owner = UUID.randomUUID().toString()
    private val pendingPositions = linkedMapOf<NovelKey, ReadPosition>()
    private val pendingPositionOwners = mutableMapOf<NovelKey, Any?>()
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
    private fun <T> write(beforeCommit: () -> Unit = {}, invalidatesPositionEligibility: Boolean = false, action: () -> T): T = try {
        database.transaction(beforeCommit, action)
    } catch (failure: Throwable) {
        // Only chapter-list changes can make cached scroll eligibility stale after a lost reply.
        // Autosave/content/membership failures must keep accepting the next scroll position.
        if (invalidatesPositionEligibility) positionBooks = emptyMap()
        throw failure
    }
    /** Extension artifacts live beside the store and share its process lease. */
    internal val extensionDirectory: Path = directory.resolve("extensions")

    @Synchronized internal fun <T> extensionRead(action: (Connection) -> T): T {
        database.verifyMarker()
        return action(db)
    }

    @Synchronized internal fun <T> extensionWrite(action: (Connection) -> T): T = write { action(db) }

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
        write(invalidatesPositionEligibility = true) {
            val old = book(key)
            val metadata = SavedBook(key, novel, chapters, old?.favorite ?: false, position = old?.position,
                resumeChapterUrl = old?.resumeChapterUrl)
            db.execute("INSERT INTO books VALUES(?,?,?) ON CONFLICT(source_id,novel_url) DO UPDATE SET metadata=excluded.metadata",
                key.sourceId, key.url, encodeBook(metadata).apply { remove("downloaded") }.toString())
            db.query("SELECT chapter_url FROM reading_states WHERE source_id=? AND novel_url=?", key.sourceId, key.url) { it.getString(1) }
                .filter { url -> chapters.none { it.url == url } }.forEach { url ->
                    db.execute("DELETE FROM reading_state_receipts WHERE source_id=? AND novel_url=? AND chapter_url=?", key.sourceId, key.url, url)
                    db.execute("DELETE FROM reading_states WHERE source_id=? AND novel_url=? AND chapter_url=?", key.sourceId, key.url, url)
                }
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

    @Synchronized internal fun historyRecording(): Boolean {
        database.verifyMarker()
        return db.query("SELECT value FROM settings WHERE name='record_opened_history'") { it.getString(1) }.singleOrNull() == "1"
    }

    @Synchronized internal fun setHistoryRecording(enabled: Boolean) {
        write {
            db.execute("INSERT INTO settings VALUES('record_opened_history',?) ON CONFLICT(name) DO UPDATE SET value=excluded.value",
                if (enabled) "1" else "0")
        }
    }

    /** Unknown or missing values fall back to the defaults, so a newer build's choice never blocks startup. */
    @Synchronized fun appearance(): DesktopAppearance {
        database.verifyMarker()
        val values = db.query("SELECT name, value FROM settings WHERE name IN ('theme_mode','color_theme')") {
            it.getString(1) to it.getString(2)
        }.toMap()
        return DesktopAppearance(
            ThemeMode.entries.firstOrNull { it.name == values["theme_mode"] } ?: ThemeMode.SYSTEM,
            ColorTheme.entries.firstOrNull { it.name == values["color_theme"] } ?: ColorTheme.DEFAULT,
        )
    }

    @Synchronized fun setAppearance(appearance: DesktopAppearance) {
        write {
            setSetting("theme_mode", appearance.themeMode.name)
            setSetting("color_theme", appearance.colorTheme.name)
        }
    }

    @Synchronized fun setThemeMode(themeMode: ThemeMode) = write { setSetting("theme_mode", themeMode.name) }

    @Synchronized fun setColorTheme(colorTheme: ColorTheme) = write { setSetting("color_theme", colorTheme.name) }

    private fun setSetting(name: String, value: String) {
        db.execute("INSERT INTO settings VALUES(?,?) ON CONFLICT(name) DO UPDATE SET value=excluded.value", name, value)
    }

    @Synchronized internal fun openedHistory(): List<DesktopHistoryEntry> {
        database.verifyMarker()
        return db.query("""SELECT id,source_id,novel_url,chapter_url,novel_title,chapter_title,opened_at
            FROM opened_history ORDER BY opened_at DESC,id DESC""") { row ->
            DesktopHistoryEntry(row.getLong(1),
                DesktopOpenedChapter(ChapterKey(NovelKey(row.getLong(2), row.getString(3)), row.getString(4)),
                    row.getString(5), row.getString(6)), row.getLong(7))
        }
    }

    @Synchronized internal fun recordOpenedChapter(snapshot: DesktopOpenedChapter, openedAtMillis: Long) {
        val key = snapshot.key
        write {
            db.execute("""INSERT INTO opened_history(source_id,novel_url,chapter_url,novel_title,chapter_title,opened_at)
                VALUES(?,?,?,?,?,?) ON CONFLICT(source_id,novel_url,chapter_url) DO UPDATE SET
                novel_title=excluded.novel_title, chapter_title=excluded.chapter_title, opened_at=excluded.opened_at""",
                key.novel.sourceId, key.novel.url, key.url, snapshot.novelTitle, snapshot.chapterTitle, openedAtMillis)
        }
    }

    @Synchronized internal fun deleteOpenedHistory(ids: List<Long>) = write {
        ids.forEach { db.execute("DELETE FROM opened_history WHERE id=?", it) }
    }

    @Synchronized internal fun clearOpenedHistory() {
        write { db.execute("DELETE FROM opened_history") }
    }

    /** Navigation selects a chapter without inventing a position or altering a delayed checkpoint. */
    @Synchronized internal fun selectResumeChapter(key: ChapterKey): SavedBook = write {
        require(eligibility(key) == null) { "Chapter is unavailable or locked" }
        val current = requireNotNull(book(key.novel))
        if (current.resumeChapterUrl != key.url) db.execute("UPDATE books SET metadata=? WHERE source_id=? AND novel_url=?",
            encodeBook(current.copy(resumeChapterUrl = key.url)).apply { remove("downloaded") }.toString(), key.novel.sourceId, key.novel.url)
        requireNotNull(book(key.novel))
    }
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

    /** Seed only protocol identity; the body, receipt, legacy position and membership stay untouched. */
    @Synchronized internal fun readObserved(key: ChapterKey, beforeCommit: () -> Unit = {}): ObservedSavedChapter = write(beforeCommit) {
        val saved = readSnapshot(key)
        if (eligibility(key) != null) return@write ObservedSavedChapter(saved, null)
        val row = content(key) ?: run {
            db.execute("INSERT INTO contents(source_id,novel_url,chapter_url,revision) VALUES(?,?,?,?)",
                key.novel.sourceId, key.novel.url, key.url, UUID.randomUUID().toString())
            requireNotNull(content(key))
        }
        ObservedSavedChapter(saved, ChapterStorageObservation(key, owner, row.revision,
            if (row.hasContent) ChapterContentPresence.SAVED else ChapterContentPresence.ABSENT))
    }

    @Synchronized internal fun isObservationCurrent(observation: ChapterStorageObservation, beforeCommit: () -> Unit = {}): Boolean {
        if (closed) return false
        return write(beforeCommit) { observationCurrent(observation) }
    }

    private fun observationCurrent(observation: ChapterStorageObservation): Boolean {
        if (observation.scope != owner || eligibility(observation.key) != null) return false
        return content(observation.key)?.let { row ->
            row.revision == observation.generation && row.hasContent == (observation.presence == ChapterContentPresence.SAVED)
        } == true
    }

    @Synchronized internal fun registerReadingSession(observation: ChapterStorageObservation, document: CanonicalDocument,
        beforeCommit: () -> Unit): ReadingSession? {
        if (closed) return null
        return write(beforeCommit) {
            if (!canRegisterReadingSession(observation, document) || !observationCurrent(observation)) return@write null
            val previous = checkpointState(observation.key)
            val session = ReadingSession(UUID.randomUUID().toString(), observation, document, previous?.anchor)
            putCheckpoint(ReadingCheckpointState(session.binding, anchor = previous?.anchor))
            session
        }
    }

    @Synchronized internal fun commitReadingCheckpoint(command: CheckpointCommand, beforeCommit: () -> Unit): CheckpointCommit {
        if (closed) return CheckpointCommit.NotApplied(CheckpointRejection.STALE)
        return write(beforeCommit) {
            val result = decideCheckpoint(command, checkpointState(command.session.observation.key),
                observationCurrent(command.session.observation))
            if (result is CheckpointCommit.Committed && !result.replayed) {
                putCheckpoint(ReadingCheckpointState(command.session.binding, command.sequence, command.anchor))
            }
            result
        }
    }

    @Synchronized internal fun readReadingCheckpoint(key: ChapterKey): ReadingAnchor? {
        database.verifyMarker()
        if (eligibility(key) != null) return null
        return checkpointState(key)?.anchor
    }

    private fun checkpointState(key: ChapterKey): ReadingCheckpointState? = db.query(
        "SELECT state FROM reading_checkpoints WHERE source_id=? AND novel_url=? AND chapter_url=?",
        key.novel.sourceId, key.novel.url, key.url) { decodeReadingCheckpoint(it.getString(1)) }.singleOrNull()?.also {
            require(it.binding.observation.key == key) { "Checkpoint identity mismatch" }
        }

    private fun putCheckpoint(state: ReadingCheckpointState) {
        val key = state.binding.observation.key
        db.execute("""INSERT INTO reading_checkpoints VALUES(?,?,?,?) ON CONFLICT(source_id,novel_url,chapter_url)
            DO UPDATE SET state=excluded.state""", key.novel.sourceId, key.novel.url, key.url, encodeReadingCheckpoint(state))
    }

    private fun hasReadingStateChapter(key: ChapterKey) = book(key.novel)?.chapters?.any { it.url == key.url } == true
    private fun stateRecord(key: ChapterKey) = db.query("SELECT state FROM reading_states WHERE source_id=? AND novel_url=? AND chapter_url=?",
        key.novel.sourceId, key.novel.url, key.url) { decodeReadingStateRecord(it.getString(1)) }.singleOrNull()?.also {
        require(it.value.key == key) { "Reading state identity mismatch" }
    }
    private fun putStateRecord(record: ReadingStateRecord) {
        val key = record.value.key
        db.execute("INSERT INTO reading_states VALUES(?,?,?,?) ON CONFLICT(source_id,novel_url,chapter_url) DO UPDATE SET state=excluded.state",
            key.novel.sourceId, key.novel.url, key.url, encodeReadingStateRecord(record))
    }
    @Synchronized internal fun prepareReadingState(key: ChapterKey, beforeCommit: () -> Unit): PreparedReadingState? {
        if (closed) return null
        return write(beforeCommit) {
            if (!hasReadingStateChapter(key)) return@write null
            val r = stateRecord(key) ?: ReadingStateRecord(UUID.randomUUID().toString(), 0, ReadingCompletionState(key, ReadStatus.UNKNOWN, null)).also {
                db.execute("DELETE FROM reading_state_receipts WHERE source_id=? AND novel_url=? AND chapter_url=?", key.novel.sourceId, key.novel.url, key.url)
                putStateRecord(it)
            }
            PreparedReadingState(ReadingStateBinding(key, owner, r.incarnation, r.intentRevision), r.value)
        }
    }
    @Synchronized internal fun registerStateWriter(binding: ReadingStateBinding, content: ChapterStorageObservation, document: CanonicalDocument,
        kind: ReadingStateWriterKind, beforeCommit: () -> Unit): ReadingStateSession? {
        if (closed) return null
        return write(beforeCommit) {
            if (!hasReadingStateChapter(binding.key)) return@write null
            val r = stateRecord(binding.key) ?: return@write null
            if (binding.scope != owner || binding.incarnation != r.incarnation || binding.intentRevision != r.intentRevision ||
                content.key != binding.key || !canRegisterReadingSession(content, document) || !observationCurrent(content)) return@write null
            ReadingStateSession(UUID.randomUUID().toString(), binding, content, document, kind).also { putStateRecord(bindReadingStateWriter(r, it)) }
        }
    }
    @Synchronized internal fun retireStateWriter(session: ReadingStateSession, beforeCommit: () -> Unit): Boolean {
        if (closed) return false
        return write(beforeCommit) {
            if (!hasReadingStateChapter(session.binding.key)) return@write false
            val next = retireReadingStateWriter(stateRecord(session.binding.key), session, session.binding.scope == owner)
                ?: return@write false
            putStateRecord(next); true
        }
    }
    @Synchronized internal fun commitReadingState(command: ReadingStateCommand, beforeCommit: () -> Unit): ReadingStateCommit {
        if (closed) return ReadingStateCommit.NotApplied(ReadingStateRejection.STALE)
        return write(beforeCommit) {
            val key = command.binding.key
            val r = if (hasReadingStateChapter(key)) stateRecord(key) else null
            val receipt = if (r?.incarnation == command.binding.incarnation) db.query(
                "SELECT receipt FROM reading_state_receipts WHERE source_id=? AND novel_url=? AND chapter_url=? AND command_id=?",
                key.novel.sourceId, key.novel.url, key.url, command.id) { decodeReadingStateReceipt(it.getString(1)) }.singleOrNull() else null
            val transition = decideReadingState(command, r, receipt, command.binding.scope == owner,
                command.session?.let { observationCurrent(it.content) } ?: false)
            transition.record?.let {
                putStateRecord(it)
                db.execute("INSERT INTO reading_state_receipts VALUES(?,?,?,?,?)", key.novel.sourceId, key.novel.url, key.url,
                    command.id, encodeReadingStateReceipt(requireNotNull(transition.receipt)))
            }
            transition.result
        }
    }
    @Synchronized internal fun readReadingState(key: ChapterKey): ReadingCompletionState? {
        database.verifyMarker()
        return if (hasReadingStateChapter(key)) stateRecord(key)?.value ?: ReadingCompletionState(key, ReadStatus.UNKNOWN, null) else null
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
    @Synchronized fun position(key: ChapterKey, paragraph: Int, offset: Int, owner: Any? = null) {
        check(!closed) { "Desktop library is closed" }
        require(paragraph >= 0 && offset >= 0)
        val book = positionBooks[key.novel] ?: return
        require(book.chapters.any { it.url == key.url && !it.locked })
        pendingPositions[key.novel] = ReadPosition(key.url, paragraph, offset)
        pendingPositionOwners[key.novel] = owner
    }

    /** A retired flush cannot discard a newer view's legacy resume selection, even for the same chapter. */
    @Synchronized internal fun discardPendingPosition(key: ChapterKey, owner: Any) {
        if (pendingPositionOwners[key.novel] === owner && pendingPositions[key.novel]?.chapterUrl == key.url) {
            pendingPositions.remove(key.novel); pendingPositionOwners.remove(key.novel)
        }
    }

    @Synchronized fun flush() {
        write {
            refresh().forEach { book ->
                if (book.key in pendingPositions) db.execute("UPDATE books SET metadata=? WHERE source_id=? AND novel_url=?",
                    encodeBook(book).apply { remove("downloaded") }.toString(), book.key.sourceId, book.key.url)
            }
        }
        pendingPositions.clear()
        pendingPositionOwners.clear()
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
