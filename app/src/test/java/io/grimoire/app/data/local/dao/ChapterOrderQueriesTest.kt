package io.grimoire.app.data.local.dao

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterOrderQueriesTest {
    @Test fun tiedChapterNumbersKeepTheirOrderAcrossDownloadReadAndReopen() {
        val file = Files.createTempFile("chapter-order", ".db")
        val url = "jdbc:sqlite:$file"
        try {
            DriverManager.getConnection(url).use { db ->
                db.createStatement().use { sql ->
                    sql.execute("""
                        CREATE TABLE chapters (
                            id INTEGER PRIMARY KEY, novelId INTEGER, url TEXT, name TEXT,
                            uploadDate INTEGER DEFAULT 0, chapterNumber REAL, translator TEXT,
                            read INTEGER DEFAULT 0, readProgress REAL DEFAULT 0,
                            readAnchorItemIndex INTEGER DEFAULT 0, readAnchorItemOffset INTEGER DEFAULT 0,
                            downloadStatus INTEGER DEFAULT 0, queueOrder INTEGER DEFAULT 0,
                            firstReadAt INTEGER, wordCount INTEGER DEFAULT 0, locked INTEGER DEFAULT 0
                        )
                    """)
                    sql.execute("CREATE INDEX index_chapters_novelId ON chapters(novelId)")
                    sql.execute("CREATE INDEX index_chapters_downloadStatus ON chapters(downloadStatus)")
                }
                // Many novels make the per-novel index useful to the real planner.
                // Sources may omit chapterNumber (all -1) or repeat a number.
                db.autoCommit = false
                db.prepareStatement("INSERT INTO chapters(id,novelId,url,name,chapterNumber) VALUES(?,?,?,?,?)").use { insert ->
                    for (id in 1..10_000) {
                        insert.setInt(1, id)
                        insert.setInt(2, 1)
                        insert.setString(3, "chapter/$id")
                        insert.setString(4, "Chapter $id")
                        insert.setFloat(5, if (id in 150..199) 2f else -1f)
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
                db.commit()
                db.autoCommit = true
                db.createStatement().use {
                    // Exercise a legal plan after a workload changes and a new
                    // index is analyzed, while the older index's stats are stale.
                    // This reproduces the ordering hazard; it does not assert
                    // that a particular user's database has these statistics.
                    it.execute("ANALYZE")
                    it.executeUpdate("UPDATE chapters SET novelId = id / 100")
                    it.execute("CREATE INDEX index_chapters_novelId_read_downloadStatus_locked ON chapters(novelId,read,downloadStatus,locked)")
                    it.execute("ANALYZE index_chapters_novelId_read_downloadStatus_locked")
                }
                // No INDEXED BY: exercise the same plan SQLite selects for the app.
                for (query in queries) {
                    val plan = db.createStatement().use { sql ->
                        sql.executeQuery("EXPLAIN QUERY PLAN " + query.replace(":novelId", "1")).use { rows ->
                            buildList { while (rows.next()) add(rows.getString("detail")) }
                        }
                    }
                    assertTrue(plan.toString(), plan.any { it.contains("index_chapters_novelId_read_downloadStatus_locked") })
                }
                assertReadingOrder(db)
                db.createStatement().use { it.executeUpdate("UPDATE chapters SET downloadStatus=3, wordCount=20 WHERE id IN (100,102,150)") }
                assertReadingOrder(db)
                db.createStatement().use { it.executeUpdate("UPDATE chapters SET read=1, readProgress=1 WHERE id IN (101,151)") }
                assertReadingOrder(db)
            }
            DriverManager.getConnection(url).use(::assertReadingOrder)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private val queries = listOf(CHAPTERS_IN_READING_ORDER_QUERY, CHAPTER_METADATA_IN_READING_ORDER_QUERY)

    private fun assertReadingOrder(db: Connection) {
        for (query in queries) {
            db.prepareStatement(query).use { statement ->
                statement.setLong(1, 1)
                statement.executeQuery().use { rows ->
                    val ids = buildList { while (rows.next()) add(rows.getInt("id")) }
                    assertEquals((100..199).toList(), ids)
                    // Reader previous/next consumes these same ordered DAO lists.
                    assertEquals(101, ids[ids.indexOf(100) + 1])
                    assertEquals(149, ids[ids.indexOf(150) - 1])
                }
            }
        }
    }
}
