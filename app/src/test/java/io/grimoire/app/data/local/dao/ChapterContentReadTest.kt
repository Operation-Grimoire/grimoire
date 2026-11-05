package io.grimoire.app.data.local.dao

import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChapterContentReadTest {
    @Test fun largeUnicodeBodySurvivesBoundedSqlReads() = runBlocking {
        database().use { db ->
            val body = "a".repeat(CHAPTER_CONTENT_CHUNK_BYTES - 1) + "\uD83D\uDE00\u0000\u001f" + "\u0628 \u7d42 ".repeat(350_000)
            assertTrue(body.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024)
            save(db, 1, body)
            val sizes = mutableListOf<Int>()
            val restored = read(db, 1) { sizes += it }
            assertTrue("Content changed across chunk boundaries", body == restored)
            assertTrue(sizes.size > 8)
            assertTrue(sizes.all { it <= 256 * 1024 })
        }
    }

    @Test fun emptyMissingAndExactChunkMultiplesStayDistinct() = runBlocking {
        database().use { db ->
            save(db, 1, "")
            assertEquals("", read(db, 1))
            assertNull(read(db, 99))
            val exact = "a".repeat(CHAPTER_CONTENT_CHUNK_BYTES * 2)
            save(db, 2, exact)
            val sizes = mutableListOf<Int>()
            assertTrue(exact == read(db, 2) { sizes += it })
            assertEquals(listOf(CHAPTER_CONTENT_CHUNK_BYTES, CHAPTER_CONTENT_CHUNK_BYTES), sizes)
        }
    }

    @Test fun backfillSelectsIdsWithoutMaterializingLargeBodies() = runBlocking {
        database().use { db ->
            db.createStatement().use { sql ->
                sql.execute("INSERT INTO chapters VALUES (1,1,0),(2,0,0),(3,1,10),(4,1,0)")
            }
            val large = "word ".repeat(450_000)
            save(db, 1, large)
            save(db, 2, "newly read")
            save(db, 3, "already counted")
            fun ids(): List<Long> = db.createStatement().use { sql ->
                sql.executeQuery(READ_DOWNLOADS_MISSING_WORD_COUNT_QUERY).use { rows ->
                    assertEquals(1, rows.metaData.columnCount)
                    buildList { while (rows.next()) add(rows.getLong(1)) }.sorted()
                }
            }
            assertEquals(listOf(1L), ids())
            db.createStatement().use { it.executeUpdate("UPDATE chapters SET read=1 WHERE id=2") }
            assertEquals(listOf(1L, 2L), ids())
            assertTrue(large == read(db, 1))
            assertEquals("newly read", read(db, 2))
        }
    }

    private fun database(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { db ->
        db.createStatement().use { sql ->
            sql.execute("CREATE TABLE chapters (id INTEGER PRIMARY KEY, read INTEGER, wordCount INTEGER)")
            sql.execute("CREATE TABLE chapter_content (chapterId INTEGER PRIMARY KEY, content TEXT NOT NULL)")
        }
    }

    private fun save(db: Connection, id: Long, body: String) {
        db.prepareStatement("INSERT INTO chapter_content VALUES (?,?)").use {
            it.setLong(1, id)
            it.setString(2, body)
            it.executeUpdate()
        }
    }

    private suspend fun read(db: Connection, id: Long, onChunk: (Int) -> Unit = {}): String? {
        val byteLength = db.prepareStatement(CHAPTER_CONTENT_LENGTH_QUERY).use { query ->
            query.setLong(1, id)
            query.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        } ?: return null
        return db.prepareStatement(CHAPTER_CONTENT_CHUNK_QUERY).use { query ->
            readChapterContentInChunks(byteLength) { start, length ->
                query.setLong(1, start)
                query.setInt(2, length)
                query.setLong(3, id)
                query.executeQuery().use { rows ->
                    if (rows.next()) rows.getBytes(1).also { onChunk(it.size) } else null
                }
            }
        }
    }
}
