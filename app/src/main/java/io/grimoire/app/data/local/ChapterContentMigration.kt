package io.grimoire.app.data.local

/**
 * Moves downloaded chapter text out of `chapters.downloadedContent` into the
 * `chapter_content` table (schema v31 → v32), in two steps:
 *
 * 1. [moveChapterContentInBatches] runs **before Room opens the database** (see
 *    [ChapterContentPrepassFactory]) and copies content across in small, separately
 *    committed batches, nulling the old column as it goes. A single-transaction move
 *    needs WAL space roughly equal to all downloaded text (1.5 GB on a 90k-chapter
 *    test DB); batching bounds it to one batch, and an interrupted run just resumes
 *    on the next launch because each batch is atomic.
 * 2. [rebuildChaptersWithoutContent] is the Room migration body. It sweeps up any
 *    content the pre-pass didn't move (it may not have run, e.g. when upgrading from
 *    before v30), then rebuilds `chapters` without the column. Nulling a column does
 *    not compact the table, so the rebuild is what actually shrinks each row back
 *    to its metadata. With the pre-pass done it copies only metadata.
 *
 * Both run with foreign keys off (Room turns them on in `onOpen`, after migrations;
 * the pre-pass uses its own fresh connection), so dropping the old `chapters` table
 * cannot cascade into `chapter_content`.
 *
 * Written against [SqlExecutor] so a JVM test can run the exact statements on real
 * SQLite without Robolectric.
 */
internal interface SqlExecutor {
    fun exec(sql: String, args: Array<Any?> = emptyArray())

    /** First column of every row, as Long (null for SQL NULL). */
    fun queryLongs(sql: String, args: Array<Any?> = emptyArray()): List<Long?>

    /** Runs [block] atomically. Inside a Room migration this is already the case. */
    fun <T> inTransaction(block: () -> T): T
}

/** Must match what Room generates for [io.grimoire.app.data.local.entity.ChapterContentEntity]. */
internal const val CREATE_CHAPTER_CONTENT_SQL =
    "CREATE TABLE IF NOT EXISTS `chapter_content` (`chapterId` INTEGER NOT NULL, " +
        "`content` TEXT NOT NULL, PRIMARY KEY(`chapterId`), FOREIGN KEY(`chapterId`) " +
        "REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

/** Must match what Room generates for [io.grimoire.app.data.local.entity.ChapterEntity] (v32). */
private const val CREATE_CHAPTERS_NEW_SQL =
    "CREATE TABLE IF NOT EXISTS `chapters_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`novelId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, " +
        "`uploadDate` INTEGER NOT NULL, `chapterNumber` REAL NOT NULL, `translator` TEXT, " +
        "`read` INTEGER NOT NULL, `readProgress` REAL NOT NULL, `readAnchorItemIndex` INTEGER NOT NULL, " +
        "`readAnchorItemOffset` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL, " +
        "`queueOrder` INTEGER NOT NULL, `firstReadAt` INTEGER, `wordCount` INTEGER NOT NULL, " +
        "`locked` INTEGER NOT NULL, FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val CHAPTER_META_COLUMNS =
    "id, novelId, url, name, uploadDate, chapterNumber, translator, read, readProgress, " +
        "readAnchorItemIndex, readAnchorItemOffset, downloadStatus, queueOrder, firstReadAt, " +
        "wordCount, locked"

private val CHAPTER_INDEX_SQL = listOf(
    "CREATE INDEX IF NOT EXISTS `index_chapters_novelId` ON `chapters` (`novelId`)",
    "CREATE INDEX IF NOT EXISTS `index_chapters_downloadStatus` ON `chapters` (`downloadStatus`)",
    "CREATE INDEX IF NOT EXISTS `index_chapters_novelId_read_downloadStatus_locked` " +
        "ON `chapters` (`novelId`, `read`, `downloadStatus`, `locked`)",
)

internal fun SqlExecutor.chaptersHasContentColumn(): Boolean =
    queryLongs(
        "SELECT COUNT(*) FROM pragma_table_info('chapters') WHERE name = 'downloadedContent'",
    ).firstOrNull() == 1L

/**
 * Copies inline chapter text into `chapter_content` [batchSize] chapters per
 * transaction, nulling the source column in the same transaction. Walks by row id,
 * so the whole pass is linear. Returns how many chapters were moved; 0 when the
 * column is already gone.
 */
internal fun moveChapterContentInBatches(db: SqlExecutor, batchSize: Int = 100): Int {
    if (!db.chaptersHasContentColumn()) return 0
    db.exec(CREATE_CHAPTER_CONTENT_SQL)
    var lastId = 0L
    var moved = 0
    while (true) {
        // Numeric literals rather than bound args: the framework executor binds
        // everything as text, which SQLite rejects for LIMIT.
        val ids = db.queryLongs(
            "SELECT id FROM chapters WHERE id > $lastId AND downloadedContent IS NOT NULL " +
                "ORDER BY id LIMIT $batchSize",
        ).filterNotNull()
        if (ids.isEmpty()) break
        // Row ids are Longs we just read, so inlining them is injection-safe and
        // avoids SQLite's bound-parameter limit.
        val idList = ids.joinToString(",")
        db.inTransaction {
            db.exec(
                "INSERT OR REPLACE INTO chapter_content (chapterId, content) " +
                    "SELECT id, downloadedContent FROM chapters " +
                    "WHERE id IN ($idList) AND downloadedContent IS NOT NULL",
            )
            db.exec("UPDATE chapters SET downloadedContent = NULL WHERE id IN ($idList)")
        }
        moved += ids.size
        lastId = ids.last()
    }
    return moved
}

/** Room migration 31 → 32 body. Must run inside the migration's transaction. */
internal fun rebuildChaptersWithoutContent(db: SqlExecutor) {
    db.exec(CREATE_CHAPTER_CONTENT_SQL)
    // Leftovers: everything when the pre-pass didn't run, nothing when it finished.
    db.exec(
        "INSERT OR REPLACE INTO chapter_content (chapterId, content) " +
            "SELECT id, downloadedContent FROM chapters WHERE downloadedContent IS NOT NULL",
    )
    // `chapters` is AUTOINCREMENT; keep its high-water mark so ids of deleted
    // chapters are never handed out again after the rebuild.
    val oldSeq = db.queryLongs("SELECT seq FROM sqlite_sequence WHERE name = 'chapters'")
        .firstOrNull() ?: 0L
    db.exec("DROP TABLE IF EXISTS `chapters_new`")
    db.exec(CREATE_CHAPTERS_NEW_SQL)
    db.exec("INSERT INTO chapters_new ($CHAPTER_META_COLUMNS) SELECT $CHAPTER_META_COLUMNS FROM chapters")
    db.exec("DROP TABLE chapters")
    db.exec("ALTER TABLE chapters_new RENAME TO chapters")
    CHAPTER_INDEX_SQL.forEach { db.exec(it) }
    db.exec("DELETE FROM sqlite_sequence WHERE name = 'chapters'")
    db.exec(
        "INSERT INTO sqlite_sequence (name, seq) " +
            "SELECT 'chapters', MAX($oldSeq, COALESCE((SELECT MAX(id) FROM chapters), 0))",
    )
}
