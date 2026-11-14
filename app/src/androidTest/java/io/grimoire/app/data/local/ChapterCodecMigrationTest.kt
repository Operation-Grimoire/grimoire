package io.grimoire.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.di.MIGRATION_32_33
import io.grimoire.app.data.book.BookPage
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.epub.EpubExporter
import io.grimoire.app.data.epub.EpubParser
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.decodeChapterContent
import io.grimoire.reading.encodeChapterContent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room/Android SQLite migration, with a frozen pre-change schema, never the user's DB. */
@RunWith(AndroidJUnit4::class)
class ChapterCodecMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "chapter-codec-409-test.db"
    private var database: AppDatabase? = null
    private val legacy = "Legacy prose\u001c<i>Legacy prose</i>\u001f\u001d\u001f\u001ehttps://example.org/i"
    private val metadata = ChapterEntity(id = 1, novelId = 1, url = "/legacy", name = "Legacy",
        uploadDate = 1234, chapterNumber = 1.5f, translator = "T", read = true,
        readProgress = 0.625f, readAnchorItemIndex = 2, readAnchorItemOffset = 17,
        downloadStatus = 3, queueOrder = 9, firstReadAt = 789, wordCount = 2)

    @After fun cleanup() {
        database?.close()
        context.deleteDatabase(name)
    }

    private fun createLegacy(content: String = legacy) {
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            val schema = instrumentation.context.assets.open("chapter-codec/v32.sql")
                .bufferedReader().use { it.readText() }
                .lineSequence().filterNot { it.startsWith("--") }.joinToString("\n")
            schema.split(';').filter { it.isNotBlank() }.forEach(db::execSQL)
            db.execSQL("""INSERT INTO novels (id,sourceId,url,title,genres,status,favorite,lastUpdated,
                chapterSortOrder,lastReadAt,lastAccessedAt,readerTextAlign,notifyOnNewChapters,
                notifyOnNewLockedChapters,autoDownloadNewChapters)
                VALUES (1,409,'/book','Codec fixture','',0,1,0,0,0,0,0,0,0,0)""")
            db.execSQL("""INSERT INTO chapters (id,novelId,url,name,uploadDate,chapterNumber,translator,
                read,readProgress,readAnchorItemIndex,readAnchorItemOffset,downloadStatus,queueOrder,
                firstReadAt,wordCount,locked) VALUES (1,1,'/legacy','Legacy',1234,1.5,'T',1,0.625,2,17,3,9,789,2,0)""")
            db.execSQL("INSERT INTO chapter_content (chapterId,content) VALUES (1,?)", arrayOf(content))
            assertEquals(32, db.version)
        }
    }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(MIGRATION_32_33).build().also {
            database = it
            // Forces the real migration and Room's full schema/identity validation.
            assertEquals(33, it.openHelper.writableDatabase.version)
        }

    @Test fun legacyBytesAndAllReadingMetadataSurviveMigrationAndReopen() = runBlocking {
        val large = "中文😀".repeat(310_000)
        val content = legacy + "\u001f" + large
        createLegacy(content)
        var db = open()
        assertEquals(metadata, db.chapterDao().getByUrl(1, "/legacy"))
        val stored = db.chapterDao().getContent(1)!!
        assertEquals(1, stored.version)
        assertArrayEquals(content.toByteArray(Charsets.UTF_8), stored.content.toByteArray(Charsets.UTF_8))
        assertTrue(db.chapterDao().getContentByteLength(1)!! > 2 * 1024 * 1024)
        val pages = decodeChapterContent(stored)
        assertEquals(PageContent.Text("Legacy prose", "<i>Legacy prose</i>"), pages[0].content)
        assertTrue(pages[1].content is PageContent.Separator)
        assertEquals(PageContent.Image("https://example.org/i"), pages[2].content)
        assertEquals(PageContent.Text(large), pages[3].content)
        // The export path also uses chunked reads, not a body-sized CursorWindow.
        val exported = db.chapterDao().getDownloadedChaptersWithContent(1).single()
        assertEquals(content, exported.content)
        assertEquals(1, exported.contentVersion)
        val file = context.cacheDir.resolve("codec-409-legacy.epub")
        try {
            EpubExporter(context, db.novelDao(), db.chapterDao(), ChapterImageStore(context))
                .export(1, Uri.fromFile(file)).getOrThrow()
            val parsed = EpubParser.parse(file, "legacy.epub")
            val text = parsed.chapters.flatMap { it.pages }.filterIsInstance<BookPage.Text>().map { it.text }
            assertTrue(text.contains("Legacy prose"))
            assertTrue(text.contains(large))
        } finally { file.delete() }
        db.close()
        db = open()
        assertEquals(stored, db.chapterDao().getContent(1))
        assertEquals(metadata, db.chapterDao().getByUrl(1, "/legacy"))
    }

    @Test fun newWritesUseV2AndKeepProgressAcrossRestart() = runBlocking {
        createLegacy()
        var db = open()
        val text = "one\u0000\u001ctwo\u001dthree\u001efour\u001ffive"
        val pages = listOf(NovelPage(0, PageContent.Text(text, "<i>$text</i>")),
            NovelPage(1, PageContent.Image("https://example.org/\u001c\u001d\u001e\u001f")),
            NovelPage(2, PageContent.Separator()), NovelPage(3, PageContent.Text("", "")))
        val encoded = encodeChapterContent(pages)
        db.chapterDao().setDownloadStatus(1, 2)
        assertTrue(db.chapterDao().finishDownload(1, encoded))
        assertEquals("2", db.chapterDao().getContentVersionToken(1))
        assertEquals(metadata.copy(wordCount = 5), db.chapterDao().getByUrl(1, "/legacy"))
        val file = context.cacheDir.resolve("codec-409-destination.epub")
        val sentinel = "Do not truncate an existing destination".toByteArray()
        file.writeBytes(sentinel)
        try {
            val result = EpubExporter(context, db.novelDao(), db.chapterDao(), ChapterImageStore(context))
                .export(1, Uri.fromFile(file))
            assertTrue(result.isFailure)
            assertArrayEquals(sentinel, file.readBytes())
        } finally { file.delete() }
        assertFalse(db.chapterDao().finishDownload(1, encodeChapterContent(emptyList())))
        db.close()
        db = open()
        val stored = db.chapterDao().getContent(1)!!
        assertEquals(encoded, stored)
        val actual = decodeChapterContent(stored)
        assertEquals(pages[0], actual[0])
        assertEquals(pages[1], actual[1])
        assertTrue(actual[2].content is PageContent.Separator)
        assertEquals(pages[3], actual[3])
        assertEquals(metadata.copy(wordCount = 5), db.chapterDao().getByUrl(1, "/legacy"))
    }

    @Test fun unsupportedWritesRollBackAndUnknownStoredVersionsFailWithoutRewriting() = runBlocking {
        createLegacy()
        val db = open()
        val before = db.chapterDao().getContent(1)
        try {
            db.chapterDao().setDownloadedContent(1, StoredChapterContent("unsupported", 99), 3)
            fail("Unsupported write was committed")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, db.chapterDao().getContent(1))
        assertEquals(metadata, db.chapterDao().getByUrl(1, "/legacy"))
        db.openHelper.writableDatabase.execSQL("UPDATE chapter_content SET contentVersion=99 WHERE chapterId=1")
        val unknown = db.chapterDao().getContent(1)!!
        try { decodeChapterContent(unknown); fail("Unknown stored version was treated as legacy") }
        catch (_: IllegalArgumentException) { }
        assertEquals(StoredChapterContent(legacy, 99), db.chapterDao().getContent(1))
        assertEquals(metadata, db.chapterDao().getByUrl(1, "/legacy"))
        for (token in listOf("'not-a-version'", "2.5", "4294967298")) {
            db.openHelper.writableDatabase.execSQL("UPDATE chapter_content SET contentVersion=$token WHERE chapterId=1")
            try { db.chapterDao().getContent(1); fail("Version was coerced to a supported integer") }
            catch (_: IllegalArgumentException) { }
            db.openHelper.readableDatabase.query("SELECT CAST(content AS BLOB) FROM chapter_content WHERE chapterId=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertArrayEquals(legacy.toByteArray(Charsets.UTF_8), cursor.getBlob(0))
            }
        }
    }
}
