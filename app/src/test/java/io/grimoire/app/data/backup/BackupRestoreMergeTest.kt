package io.grimoire.app.data.backup

import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreMergeTest {
    private val local = ChapterEntity(
        id = 12, novelId = 4, url = "chapter", name = "Chapter", readProgress = .6f,
        readAnchorItemIndex = 42, readAnchorItemOffset = 17, locked = true,
        downloadStatus = 2, queueOrder = 99, wordCount = 1000,
    )

    @Test fun `old backup preserves same-device position locks and downloaded identity`() {
        val old = Json.decodeFromString<BackupChapter>(
            """{"url":"chapter","name":"Chapter","readProgress":0.5}"""
        )
        val restored = mergeBackupChapter(old, local, 4)
        assertEquals(local, restored)
    }

    @Test fun `new backup carries position and lock state across devices`() {
        val chapter = BackupChapter("chapter", "Chapter", readProgress = .8f,
            readAnchorItemIndex = 60, readAnchorItemOffset = 9, locked = true)
        val roundTrip = Json.decodeFromString<BackupChapter>(Json.encodeToString(chapter))
        val restored = mergeBackupChapter(roundTrip, null, 15)
        assertEquals(60, restored.readAnchorItemIndex)
        assertEquals(9, restored.readAnchorItemOffset)
        assertEquals(.8f, restored.readProgress)
        assertTrue(restored.locked)
        assertEquals(0, restored.downloadStatus)
    }

    @Test fun `anchors follow newer progress and keep local lock knowledge`() {
        val backup = BackupChapter("chapter", "Chapter", readProgress = .8f,
            readAnchorItemIndex = 60, readAnchorItemOffset = 9, locked = false)
        val newer = mergeBackupChapter(backup, local, 4)
        assertEquals(60, newer.readAnchorItemIndex)
        assertEquals(9, newer.readAnchorItemOffset)
        assertTrue(newer.locked)
        val older = mergeBackupChapter(backup.copy(readProgress = .2f), local, 4)
        assertEquals(42, older.readAnchorItemIndex)
        assertEquals(17, older.readAnchorItemOffset)
        assertEquals(.6f, older.readProgress)
    }

    @Test fun `new-device EPUB is skipped while existing EPUB state can merge`() {
        val epub = BackupNovel(LOCAL_SOURCE_ID, "epub:book", "Book", favorite = true)
        assertNull(mergeBackupNovel(epub, null, null))
        val existing = NovelEntity(id = 25, sourceId = LOCAL_SOURCE_ID, url = epub.url,
            title = epub.title, customCoverPath = "device-cover")
        val restored = mergeBackupNovel(epub, existing, 3)!!
        assertEquals(25L, restored.id)
        assertEquals("device-cover", restored.customCoverPath)
        assertTrue(restored.favorite)
        assertEquals(3L, restored.categoryId)
        assertNotNull(mergeBackupNovel(epub.copy(sourceId = 100), null, null))
    }
}
