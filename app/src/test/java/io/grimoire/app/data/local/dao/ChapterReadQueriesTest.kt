package io.grimoire.app.data.local.dao

import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterReadQueriesTest {
    @Test fun sharedChapterUrlsStayInsideTheRequestedSourceAndNovel() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE novels (id INTEGER, sourceId INTEGER, url TEXT)")
                sql.execute("CREATE TABLE chapters (id INTEGER, novelId INTEGER, url TEXT, read INTEGER)")
                sql.execute("INSERT INTO novels VALUES (1,11,'one'),(2,11,'two'),(3,22,'two'),(4,$LOCAL_SOURCE_ID,'local')")
                sql.execute("INSERT INTO chapters VALUES (100,1,'shared',0),(200,2,'shared',0),(300,3,'shared',0),(400,4,'shared',0)")
            }
            db.prepareStatement(CHAPTER_BY_NOVEL_URL_QUERY).use { query ->
                fun selected(sourceId: Long, novelUrl: String): Long? {
                    query.setLong(1, sourceId)
                    query.setString(2, novelUrl)
                    query.setString(3, "shared")
                    return query.executeQuery().use { if (it.next()) it.getLong("id") else null }
                }
                assertEquals(200L, selected(11, "two"))
                assertEquals(300L, selected(22, "two"))
                assertEquals(400L, selected(LOCAL_SOURCE_ID, "local"))
                assertEquals(null, selected(22, "one"))
                assertEquals(null, selected(11, "missing"))

                db.prepareStatement(CHAPTER_BY_NOVEL_ID_QUERY).use { offline ->
                    fun selectedById(novelId: Long, novelUrl: String): Long? {
                        offline.setLong(1, novelId)
                        offline.setString(2, novelUrl)
                        offline.setString(3, "shared")
                        return offline.executeQuery().use { if (it.next()) it.getLong("id") else null }
                    }
                    // Stored identity works without resolving an installed package.
                    assertEquals(200L, selectedById(2, "two"))
                    assertEquals(300L, selectedById(3, "two"))
                    assertEquals(400L, selectedById(4, "local"))
                    assertEquals(null, selectedById(2, "one"))
                    assertEquals(null, selectedById(99, "two"))
                }

                val selectedId = checkNotNull(selected(11, "two"))
                db.createStatement().use { sql ->
                    sql.executeUpdate("UPDATE chapters SET read=1 WHERE id=$selectedId")
                    sql.executeQuery("SELECT id FROM chapters WHERE read=1").use {
                        assertTrue(it.next())
                        assertEquals(200L, it.getLong(1))
                        assertFalse(it.next())
                    }
                }
            }
        }
    }
}
