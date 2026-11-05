package io.grimoire.app.data.local.dao

import io.grimoire.app.data.download.ChapterDownloadStatus
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChapterObservationQueriesTest {
    @Test fun statsAreScopedToOneNovelIncludingNonFavorites() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE chapters (novelId INTEGER, read INTEGER, downloadStatus INTEGER, locked INTEGER)")
                sql.execute("INSERT INTO chapters VALUES (1,1,3,0),(1,0,5,1),(2,1,6,1),(2,1,7,0)")
            }
            db.prepareStatement(NOVEL_CHAPTER_STATS_QUERY).use { query ->
                query.setLong(1, 1)
                query.executeQuery().use { rows ->
                    rows.next()
                    assertEquals(1L, rows.getLong("novelId"))
                    assertEquals(2, rows.getInt("total"))
                    assertEquals(1, rows.getInt("readCount"))
                    assertEquals(2, rows.getInt("downloadedCount"))
                    assertEquals(1, rows.getInt("lockedCount"))
                    assertFalse(rows.next())
                }
                query.setLong(1, 99)
                query.executeQuery().use { assertFalse(it.next()) }
            }
        }
    }

    @Test fun countIncludesRefreshesAndHonorsHiddenCategories() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE chapters (novelId INTEGER, downloadStatus INTEGER)")
                sql.execute("CREATE TABLE novels (id INTEGER, categoryId INTEGER)")
                sql.execute("CREATE TABLE categories (id INTEGER, isHidden INTEGER)")
                sql.execute("INSERT INTO categories VALUES (10,1),(20,0)")
                sql.execute("INSERT INTO novels VALUES (1,10),(2,20),(3,NULL)")
                for (novel in 1..3) for (status in ChapterDownloadStatus.entries.indices) {
                    sql.execute("INSERT INTO chapters VALUES ($novel,$status)")
                }
                val query = ACTIVE_DOWNLOAD_COUNT_QUERY.replace(
                    ":statuses", ChapterDownloadStatus.IN_FLIGHT_ORDINALS.joinToString(","),
                )
                db.prepareStatement(query).use { count ->
                    count.setBoolean(1, false)
                    count.executeQuery().use { it.next(); assertEquals(12, it.getInt(1)) }
                    count.setBoolean(1, true)
                    count.executeQuery().use { it.next(); assertEquals(8, it.getInt(1)) }
                    sql.execute("UPDATE chapters SET downloadStatus=3 WHERE downloadStatus IN (1,2,5,6)")
                    count.executeQuery().use { it.next(); assertEquals(0, it.getInt(1)) }
                }
            }
        }
    }
}
