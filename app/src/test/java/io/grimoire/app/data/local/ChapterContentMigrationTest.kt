package io.grimoire.app.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real v31 → v32 chapter-content migration code against real SQLite
 * (sqlite-jdbc), on a v31-shaped database: the batched pre-pass, the Room migration
 * body, and the two together. Room's own schema check isn't available on the JVM,
 * so the result is compared column-by-column with the schema Room generates for v32.
 */
class ChapterContentMigrationTest {

    private lateinit var conn: Connection
    private lateinit var db: JdbcExecutor
    private lateinit var metadataBefore: List<String>

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        db = JdbcExecutor(conn)
        V31_SCHEMA.forEach(db::exec)
        db.exec("INSERT INTO novels (id) VALUES (1), (2)")
        // ids 1..10; even ids downloaded. id 11 inserted then deleted so the
        // AUTOINCREMENT high-water mark (11) is above MAX(id) (10).
        for (id in 1..11) {
            val content = if (id % 2 == 0) "'body $id'" else "NULL"
            db.exec(
                "INSERT INTO chapters (id, novelId, url, name, uploadDate, chapterNumber, translator, " +
                    "read, readProgress, readAnchorItemIndex, readAnchorItemOffset, downloadStatus, " +
                    "downloadedContent, queueOrder, firstReadAt, wordCount, locked) VALUES " +
                    // Every column gets its own value, so a column-copy mix-up changes the row.
                    "($id, ${if (id <= 5) 1 else 2}, 'u$id', 'c$id', ${id + 1000}, $id.5, " +
                    "${if (id % 4 == 0) "'t$id'" else "NULL"}, ${id % 3 == 0}, 0.$id, " +
                    "${id + 100}, ${id + 200}, ${if (id % 2 == 0) 3 else 0}, $content, ${id + 300}, " +
                    "${if (id == 4) 1234 else "NULL"}, ${id * 10}, ${id == 9})",
            )
        }
        db.exec("DELETE FROM chapters WHERE id = 11")
        metadataBefore = db.rows(METADATA_ROWS)
    }

    @After
    fun tearDown() = conn.close()

    @Test
    fun `pre-pass then migration keeps every body and all metadata`() {
        assertEquals(5, moveChapterContentInBatches(db, batchSize = 2))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM chapters WHERE downloadedContent IS NOT NULL"))
        migrate()
        assertMigrated()
    }

    @Test
    fun `migration alone moves everything when the pre-pass never ran`() {
        migrate()
        assertMigrated()
    }

    @Test
    fun `interrupted pre-pass resumes without loss`() {
        val flaky = object : SqlExecutor by db {
            var commits = 0
            override fun <T> inTransaction(block: () -> T): T {
                if (commits == 1) error("killed")
                return db.inTransaction(block).also { commits++ }
            }
        }
        runCatching { moveChapterContentInBatches(flaky, batchSize = 2) }
        assertEquals(2L, db.long("SELECT COUNT(*) FROM chapter_content"))
        assertEquals(3, moveChapterContentInBatches(db, batchSize = 2))
        migrate()
        assertMigrated()
    }

    @Test
    fun `pre-pass is a no-op once the column is gone`() {
        migrate()
        assertEquals(0, moveChapterContentInBatches(db))
    }

    private fun migrate() {
        // Room runs migrations in one transaction with foreign keys off.
        conn.autoCommit = false
        db.withSecureDeleteOff { rebuildChaptersWithoutContent(db.inOpenTransaction()) }
        conn.commit()
        conn.autoCommit = true
    }

    private fun assertMigrated() {
        assertFalse(db.chaptersHasContentColumn())
        assertEquals(schemaOf(conn), schemaOf(roomV32()))

        assertEquals(listOf(2L, 4L, 6L, 8L, 10L), db.longs("SELECT chapterId FROM chapter_content ORDER BY chapterId"))
        for (id in listOf(2, 4, 6, 8, 10)) {
            assertEquals("body $id", db.string("SELECT content FROM chapter_content WHERE chapterId = $id"))
        }
        assertEquals(metadataBefore, db.rows(METADATA_ROWS))

        // Deleted ids are never reused.
        assertEquals(11L, db.long("SELECT seq FROM sqlite_sequence WHERE name = 'chapters'"))

        // With Room's runtime settings: no dangling keys, and deletes cascade
        // novel → chapter → content.
        db.exec("PRAGMA foreign_keys = ON")
        assertTrue(db.longs("PRAGMA foreign_key_check").isEmpty())
        db.exec("DELETE FROM chapters WHERE id = 2")
        assertEquals(0L, db.long("SELECT COUNT(*) FROM chapter_content WHERE chapterId = 2"))
        db.exec("DELETE FROM novels WHERE id = 2")
        assertEquals(listOf(4L), db.longs("SELECT chapterId FROM chapter_content"))
    }

    private fun roomV32(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
        val e = JdbcExecutor(c)
        e.exec(NOVELS_TABLE)
        ROOM_V32_CHAPTER_SCHEMA.forEach(e::exec)
    }

    /** Columns, foreign keys and indices per table: what Room's schema check compares. */
    private fun schemaOf(c: Connection): Map<String, List<String>> {
        val e = JdbcExecutor(c)
        return listOf("chapters", "chapter_content").associateWith { t ->
            e.rows("SELECT name, type, \"notnull\", pk, dflt_value FROM pragma_table_info('$t') ORDER BY cid") +
                e.rows("SELECT \"table\", \"from\", \"to\", on_update, on_delete FROM pragma_foreign_key_list('$t')") +
                e.rows(
                    "SELECT il.name, il.\"unique\", group_concat(ii.name) FROM pragma_index_list('$t') il " +
                        "JOIN pragma_index_info(il.name) ii WHERE il.origin = 'c' GROUP BY il.name ORDER BY il.name",
                )
        }
    }

    private class JdbcExecutor(private val c: Connection) : SqlExecutor {
        override fun exec(sql: String, args: Array<Any?>) {
            c.prepareStatement(sql).use { st ->
                args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
                st.execute()
            }
        }

        override fun queryLongs(sql: String, args: Array<Any?>): List<Long?> = c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getLong(1).takeUnless { rs.wasNull() }) }
            }
        }

        override fun <T> inTransaction(block: () -> T): T {
            c.autoCommit = false
            try {
                return block().also { c.commit() }
            } catch (t: Throwable) {
                c.rollback()
                throw t
            } finally {
                c.autoCommit = true
            }
        }

        /** Like Room's migration executor: the caller already holds the transaction. */
        fun inOpenTransaction(): SqlExecutor = object : SqlExecutor by this {
            override fun <T> inTransaction(block: () -> T): T = block()
        }

        fun long(sql: String) = queryLongs(sql).single()!!
        fun longs(sql: String) = queryLongs(sql)
        fun string(sql: String): String = c.createStatement().use { it.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) } }
        fun rows(sql: String): List<String> = c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val n = rs.metaData.columnCount
                buildList { while (rs.next()) add((1..n).joinToString("|") { rs.getString(it) ?: "NULL" }) }
            }
        }
    }

    private companion object {
        /** Every chapter column except the removed one. */
        const val METADATA_ROWS =
            "SELECT id, novelId, url, name, uploadDate, chapterNumber, translator, read, readProgress, " +
                "readAnchorItemIndex, readAnchorItemOffset, downloadStatus, queueOrder, firstReadAt, " +
                "wordCount, locked FROM chapters ORDER BY id"

        const val NOVELS_TABLE = "CREATE TABLE `novels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"

        /** v31 as Room generated it (chapters still carries downloadedContent). */
        val V31_SCHEMA = listOf(
            NOVELS_TABLE,
            "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `novelId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `uploadDate` INTEGER NOT NULL, `chapterNumber` REAL NOT NULL, `translator` TEXT, `read` INTEGER NOT NULL, `readProgress` REAL NOT NULL, `readAnchorItemIndex` INTEGER NOT NULL, `readAnchorItemOffset` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL, `downloadedContent` TEXT, `queueOrder` INTEGER NOT NULL, `firstReadAt` INTEGER, `wordCount` INTEGER NOT NULL, `locked` INTEGER NOT NULL, FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_chapters_novelId` ON `chapters` (`novelId`)",
            "CREATE INDEX IF NOT EXISTS `index_chapters_downloadStatus` ON `chapters` (`downloadStatus`)",
            "CREATE INDEX IF NOT EXISTS `index_chapters_novelId_read_downloadStatus_locked` ON `chapters` (`novelId`, `read`, `downloadStatus`, `locked`)",
        )

        /** Copied verbatim from Room's generated AppDatabase_Impl for v32. */
        val ROOM_V32_CHAPTER_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `novelId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `uploadDate` INTEGER NOT NULL, `chapterNumber` REAL NOT NULL, `translator` TEXT, `read` INTEGER NOT NULL, `readProgress` REAL NOT NULL, `readAnchorItemIndex` INTEGER NOT NULL, `readAnchorItemOffset` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL, `queueOrder` INTEGER NOT NULL, `firstReadAt` INTEGER, `wordCount` INTEGER NOT NULL, `locked` INTEGER NOT NULL, FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_chapters_novelId` ON `chapters` (`novelId`)",
            "CREATE INDEX IF NOT EXISTS `index_chapters_downloadStatus` ON `chapters` (`downloadStatus`)",
            "CREATE INDEX IF NOT EXISTS `index_chapters_novelId_read_downloadStatus_locked` ON `chapters` (`novelId`, `read`, `downloadStatus`, `locked`)",
            "CREATE TABLE IF NOT EXISTS `chapter_content` (`chapterId` INTEGER NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`chapterId`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
