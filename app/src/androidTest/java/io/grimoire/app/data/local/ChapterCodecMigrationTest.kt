package io.grimoire.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.ChapterContentEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.ChapterLoader
import io.grimoire.reading.ChapterOrigin
import io.grimoire.reading.ChapterPageAccess
import io.grimoire.reading.ChapterLoadException
import io.grimoire.reading.ChapterLoadFailure
import io.grimoire.reading.NovelKey
import io.grimoire.reading.storage.SavedChapterRepository
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
    private val key = ChapterKey(NovelKey(409, "/book"), "/legacy")
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
        .addMigrations(MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36).build().also {
            database = it
            // Forces the real migration and Room's full schema/identity validation.
            assertEquals(36, it.openHelper.writableDatabase.version)
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
        val repository = SavedChapterRepository(RoomSavedChapterDao(db))
        assertEquals(encodeChapterContent(pages), encodeChapterContent(repository.read(key)!!))
        assertNull(repository.read(key.copy(novel = NovelKey(410, "/book"))))
        assertNull(repository.read(key.copy(novel = NovelKey(409, "/different-book"))))
        assertNull(repository.read(key.copy(url = "/missing")))
        db.chapterDao().insertAll(listOf(metadata.copy(id = 4, url = "/no-content", downloadStatus = 0)))
        assertNull(repository.read(key.copy(url = "/no-content")))
        val otherNovel = db.novelDao().upsert(NovelEntity(sourceId = 410, url = "/book", title = "Other source"))
        db.chapterDao().insertAll(listOf(metadata.copy(id = 9, novelId = otherNovel)))
        db.chapterDao().setDownloadedContent(9,
            encodeChapterContent(listOf(NovelPage(0, PageContent.Text("Other source")))), 3)
        assertEquals(PageContent.Text("Other source"),
            repository.read(key.copy(novel = NovelKey(410, "/book")))!!.single().content)
        // The export path also uses chunked reads, not a body-sized CursorWindow.
        assertEquals(listOf(metadata), db.chapterDao().getDownloadedChapterMetadata(1))
        db.chapterDao().insertAll(listOf(metadata.copy(id = 2, url = "/new", name = "New chapter", chapterNumber = 2f)))
        db.chapterDao().setDownloadedContent(2,
            encodeChapterContent(listOf(NovelPage(0, PageContent.Text("New format chapter")))), 3)
        db.chapterDao().insertAll(listOf(metadata.copy(id = 3, url = "/empty", name = "Empty", chapterNumber = 3f)))
        db.chapterDao().upsertContent(ChapterContentEntity(3, ""))
        assertEquals(listOf(1L, 2L), db.chapterDao().getDownloadedChapterMetadata(1).map { it.id })
        val file = context.cacheDir.resolve("codec-409-legacy.epub")
        try {
            val result = EpubExporter(context, db, ChapterImageStore(context))
                .export(1, Uri.fromFile(file)).getOrThrow()
            assertEquals(2, result.chapterCount)
            val parsed = EpubParser.parse(file, "legacy.epub")
            val text = parsed.chapters.flatMap { it.pages }.filterIsInstance<BookPage.Text>().map { it.text }
            assertTrue(text.contains("Legacy prose"))
            assertTrue(text.contains(large))
            assertTrue(text.contains("New format chapter"))
        } finally { file.delete() }
        db.close()
        db = open()
        assertEquals(stored, db.chapterDao().getContent(1))
        assertEquals(encodeChapterContent(pages),
            encodeChapterContent(SavedChapterRepository(RoomSavedChapterDao(db)).read(key)!!))
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
        db.chapterDao().setDownloadedContent(1, encoded, 3)
        assertEquals("2", db.chapterDao().getContentVersionToken(1))
        assertEquals(metadata.copy(wordCount = 5), db.chapterDao().getByUrl(1, "/legacy"))
        // Write a valid chapter first, then fail on the later invalid chapter.
        // Even a partially generated temporary ZIP must not reach the destination.
        db.chapterDao().insertAll(listOf(metadata.copy(id = 2, url = "/first", name = "First", chapterNumber = 0.5f)))
        db.chapterDao().setDownloadedContent(2,
            encodeChapterContent(listOf(NovelPage(0, PageContent.Text("Valid first chapter")))), 3)
        val file = context.cacheDir.resolve("codec-409-destination.epub")
        val sentinel = "Do not truncate an existing destination".toByteArray()
        file.writeBytes(sentinel)
        val temporaryFiles = context.cacheDir.list()!!.filter { it.startsWith("grimoire-export-") }.toSet()
        try {
            val result = EpubExporter(context, db, ChapterImageStore(context))
                .export(1, Uri.fromFile(file))
            assertTrue(result.isFailure)
            assertArrayEquals(sentinel, file.readBytes())
            assertEquals(temporaryFiles, context.cacheDir.list()!!.filter { it.startsWith("grimoire-export-") }.toSet())
        } finally { file.delete() }
        db.close()
        db = open()
        val stored = db.chapterDao().getContent(1)!!
        assertEquals(encoded, stored)
        assertEquals(encoded, encodeChapterContent(SavedChapterRepository(RoomSavedChapterDao(db)).read(key)!!))
        val actual = decodeChapterContent(stored)
        assertEquals(pages[0], actual[0])
        assertEquals(pages[1], actual[1])
        assertTrue(actual[2].content is PageContent.Separator)
        assertEquals(pages[3], actual[3])
        assertEquals(metadata.copy(wordCount = 5), db.chapterDao().getByUrl(1, "/legacy"))
    }

    @Test fun sharedLoadingScenariosThroughTheRealRoomAdapter() = runBlocking {
        createLegacy()
        val db = open()
        val loader = ChapterLoader(SavedChapterRepository(RoomSavedChapterDao(db)))
        val pages = listOf(NovelPage(0, PageContent.Text("Original")))
        var resolutions = 0
        val offline: suspend () -> ChapterPageAccess = {
            resolutions++
            ChapterPageAccess.Unavailable(ChapterLoadFailure.OFFLINE)
        }
        val source: suspend () -> ChapterPageAccess = {
            resolutions++
            ChapterPageAccess.Available { pages }
        }
        assertEquals(ChapterOrigin.SAVED, loader.load(key, emptyList(), offline).origin)
        for (original in listOf(pages, emptyList())) {
            val stored = encodeChapterContent(original)
            db.chapterDao().upsertContent(ChapterContentEntity(1, stored))
            val result = loader.load(key, pages, offline)
            assertEquals(ChapterOrigin.SAVED, result.origin)
            assertEquals(stored, encodeChapterContent(result.pages))
        }
        db.openHelper.writableDatabase.execSQL("UPDATE chapter_content SET contentVersion=99 WHERE chapterId=1")
        val corrupt = db.chapterDao().getContent(1)
        try { loader.load(key, pages, source); fail("Corrupt content triggered fallback") }
        catch (_: IllegalArgumentException) { }
        assertEquals(corrupt, db.chapterDao().getContent(1))
        assertEquals(0, resolutions)
        val missing = key.copy(url = "/missing")
        assertEquals(ChapterOrigin.PRELOADED, loader.load(missing, emptyList(), offline).origin)
        assertEquals(0, resolutions)
        assertEquals(ChapterOrigin.SOURCE, loader.load(missing, sourceAccess = source).origin)
        assertEquals(1, resolutions)
        try { loader.load(missing, sourceAccess = offline); fail("Missing offline content was loaded") }
        catch (failure: ChapterLoadException) { assertEquals(ChapterLoadFailure.OFFLINE, failure.reason) }
        assertEquals(2, resolutions)
        assertEquals(metadata, db.chapterDao().getByUrl(1, "/legacy"))
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
        try { SavedChapterRepository(RoomSavedChapterDao(db)).read(key); fail("Repository hid an unsupported version") }
        catch (_: IllegalArgumentException) { }
        assertEquals(StoredChapterContent(legacy, 99), db.chapterDao().getContent(1))
        assertEquals(metadata, db.chapterDao().getByUrl(1, "/legacy"))
        for (token in listOf("'not-a-version'", "2.5", "4294967298")) {
            db.openHelper.writableDatabase.execSQL("UPDATE chapter_content SET contentVersion=$token WHERE chapterId=1")
            try { db.chapterDao().getContent(1); fail("Version was coerced to a supported integer") }
            catch (_: IllegalArgumentException) { }
            try { SavedChapterRepository(RoomSavedChapterDao(db)).read(key); fail("Repository accepted a coerced version") }
            catch (_: IllegalArgumentException) { }
            db.openHelper.readableDatabase.query("SELECT CAST(content AS BLOB) FROM chapter_content WHERE chapterId=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertArrayEquals(legacy.toByteArray(Charsets.UTF_8), cursor.getBlob(0))
            }
        }
    }
}
