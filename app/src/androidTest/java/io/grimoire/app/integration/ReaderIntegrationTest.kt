package io.grimoire.app.integration

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.BuildConfig
import io.grimoire.app.MainActivity
import io.grimoire.app.R
import io.grimoire.app.data.book.LOCAL_PKG
import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.RoomReadingCheckpointDao
import io.grimoire.app.data.local.RoomSavedChapterDao
import io.grimoire.app.data.local.entity.*
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.testing.IntegrationTestEntryPoint
import io.grimoire.app.ui.NAV_TARGET_UPDATES
import io.grimoire.app.ui.tour.TourId
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.CheckpointCommand
import io.grimoire.reading.storage.CheckpointCommit
import io.grimoire.reading.storage.decodeReadingStateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real MainActivity/Hilt/Room/reader journeys; fixture setup never replaces application providers. */
@RunWith(AndroidJUnit4::class)
class ReaderIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val bindings get() = EntryPointAccessors.fromApplication(context, IntegrationTestEntryPoint::class.java)
    private val db get() = bindings.database()
    private var activity: ActivityScenario<MainActivity>? = null
    private val novelUrl = "local://integration-book"
    private val key = ChapterKey(NovelKey(LOCAL_SOURCE_ID, novelUrl), "/integration/first")
    private val firstName = "Integration chapter one"
    private val secondName = "Integration chapter two"
    private val paragraphs = (0 until 40).map { i ->
        "Integration paragraph %02d. ".format(i) + "A quiet reader follows the path through the valley. ".repeat(5)
    }
    private val pages = paragraphs.mapIndexed { i, text -> NovelPage(i, PageContent.Text(text)) }
    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
    private fun checkpoint(): ReadingAnchor? = io { RoomReadingCheckpointDao(db, RoomSavedChapterDao(db)).read(key) }
    private fun first() = io { requireNotNull(db.chapterDao().getByUrl(1, key.url)) }
    private fun completionRecord() = io { decodeReadingStateRecord(requireNotNull(db.readingStateStorageDao().find(1)).state) }
    private fun receipts(): Int = io {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM reading_state_receipts WHERE chapterId=1").use {
            check(it.moveToFirst()); it.getInt(0)
        }
    }
    private fun showReaderActions() {
        compose.onNodeWithText(paragraphs[0].trim()).performTouchInput { click(center) }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(context.getString(R.string.reader_mark_read)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Before fun seedOwnedEmulator() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("integrationFixtures") == "true") { "Use the dedicated disposable-emulator runner" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Integration fixtures require an emulator" }
        assertEquals(args.getString("integrationSourceSha"), BuildConfig.GIT_SHA)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (connectivity.activeNetwork != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
            assertNull("Run these local-content journeys with emulator networking disabled", connectivity.activeNetwork)
        }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        io {
            bindings.onboarding().done.set(true)
            TourId.entries.forEach { bindings.tours().completedVersion(it.key).set(Int.MAX_VALUE) }
            bindings.reader().markAsReadStrategy.set(MarkAsReadStrategy.AT_END)
            bindings.reader().grimoireEasterEggEnabled.set(false)
            bindings.analytics().crashReportsEnabled.set(false)
            bindings.analytics().usageAnalyticsEnabled.set(false)
            db.clearAllTables()
            db.novelDao().upsert(NovelEntity(id = 1, sourceId = LOCAL_SOURCE_ID, url = novelUrl, title = "Integration book", favorite = true))
            db.chapterDao().insertAll(listOf(
                ChapterEntity(id = 1, novelId = 1, url = key.url, name = firstName, chapterNumber = 1f, downloadStatus = 2),
                ChapterEntity(id = 2, novelId = 1, url = "/integration/second", name = secondName, chapterNumber = 2f, downloadStatus = 2),
            ))
            db.chapterDao().upsertContent(ChapterContentEntity(1, encodeChapterContent(pages)))
            db.chapterDao().upsertContent(ChapterContentEntity(2, encodeChapterContent(listOf(NovelPage(0, PageContent.Text("Second chapter saved offline."))))))
        }
    }

    private fun waitText(text: String, substring: Boolean = false) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openDetail() {
        val route = "novel?pkg=$LOCAL_PKG&url=${Uri.encode(novelUrl)}&sourceId=$LOCAL_SOURCE_ID"
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_NAV_TARGET, route))
        waitText(firstName)
    }
    private fun openFirst() {
        openDetail()
        compose.onNodeWithText(firstName).performScrollTo().performClick()
        waitText("Integration paragraph", true)
        compose.waitForIdle()
    }
    private fun swipe() {
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Integration paragraph", substring = true)))
            .performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.2f, durationMillis = 600) }
    }
    private fun back() {
        requireNotNull(activity).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("Integration book")
    }
    private fun snapshot(name: String) {
        val directory = File(context.filesDir, "integration-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun journey(name: String, action: () -> Unit) {
        try { action() }
        catch (failure: Throwable) { snapshot("$name-failure"); throw failure }
        finally {
            // A fault must not leak into another journey or leave its Activity running.
            io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS integration_write_fault") }
            activity?.close(); activity = null
        }
    }

    @Test fun inputBackColdReopenAndRecreationRestoreOfflinePosition() = journey("restore") {
        val original = io { db.chapterDao().getContent(1) }
        openFirst()
        assertNull(checkpoint()); assertEquals(0f, first().readProgress, 0f); assertFalse(first().read)
        repeat(2) { swipe() }
        compose.waitUntil(15_000) { (checkpoint()?.position as? AnchorPosition.Block)?.ordinal?.let { it > 0 } == true }
        back()
        val saved = requireNotNull(checkpoint())
        val ordinal = (saved.position as AnchorPosition.Block).ordinal
        assertTrue(ordinal in 1 until paragraphs.lastIndex)
        assertTrue(first().readProgress > 0f && first().readProgress < 1f)
        assertFalse(first().read); assertNull(first().firstReadAt)
        activity!!.close(); activity = null
        openFirst()
        compose.onNodeWithText(paragraphs[ordinal].trim()).assertIsDisplayed()
        assertEquals(saved, checkpoint())
        activity!!.recreate()
        waitText(paragraphs[ordinal].trim())
        compose.onNodeWithText(paragraphs[ordinal].trim()).assertIsDisplayed()
        assertEquals(saved, checkpoint()); assertEquals(original, io { db.chapterDao().getContent(1) })
        snapshot("restore-passed")
        back()
    }

    @Test fun restoredEndDoesNotMarkReadUntilExplicitNextNavigation() = journey("completion") {
        io {
            val saved = RoomSavedChapterDao(db)
            val checkpoints = RoomReadingCheckpointDao(db, saved)
            val document = CanonicalDocument.fromPages(key, pages)
            val session = requireNotNull(checkpoints.register(requireNotNull(saved.readObserved(key).observation), document))
            assertTrue(checkpoints.commit(CheckpointCommand(session, 1, ReadingAnchor(key, document.logicalRevision, AnchorPosition.End))) is CheckpointCommit.Committed)
        }
        // End restore intentionally has no first paragraph to wait for.
        val route = "novel?pkg=$LOCAL_PKG&url=${Uri.encode(novelUrl)}&sourceId=$LOCAL_SOURCE_ID"
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_NAV_TARGET, route))
        waitText(firstName); compose.onNodeWithText(firstName).performScrollTo().performClick()
        val next = context.getString(R.string.reader_next_chapter)
        waitText(next); compose.waitForIdle()
        assertFalse(first().read); assertNull(first().firstReadAt); assertEquals(0f, first().readProgress, 0f)
        assertEquals(AnchorPosition.End, checkpoint()!!.position)
        compose.onNodeWithText(next).performClick()
        waitText("Second chapter saved offline.")
        compose.waitUntil(10_000) { first().read }
        assertNotNull(first().firstReadAt)
        assertEquals(1L, completionRecord().intentRevision); assertEquals(1, receipts())
        assertEquals(0f, first().readProgress, 0f)
        assertFalse(io { requireNotNull(db.chapterDao().getByUrl(1, "/integration/second")).read })
        snapshot("completion-passed")
        back()
    }

    @Test fun visualCompletionNeedsFreshScrollAfterLayoutAndExplicitUnread() = journey("visual-completion") {
        openFirst()
        val list = compose.onNode(hasScrollToIndexAction())
        val error = context.getString(R.string.reader_completion_save_error)
        compose.waitUntil(10_000) { io { db.readingStateStorageDao().find(1) != null } && completionRecord().visual != null }
        val firstWriter = requireNotNull(completionRecord().visual).id
        swipe()
        list.performScrollToIndex(pages.size + 1)
        compose.waitForIdle()
        assertFalse(first().read)
        assertEquals(0, receipts())

        fun approachEnd() {
            list.performScrollToIndex(pages.size - 3)
            repeat(5) {
                if (!first().read && compose.onAllNodesWithText(error).fetchSemanticsNodes().isEmpty()) swipe()
            }
        }
        approachEnd()
        compose.waitUntil(10_000) { first().read }
        val at = requireNotNull(first().firstReadAt)
        assertEquals(0L, completionRecord().intentRevision)
        assertEquals(1, receipts())

        list.performTouchInput { click(center) }
        val markUnread = context.getString(R.string.reader_mark_unread)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(markUnread).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(markUnread).performClick()
        compose.waitUntil(10_000) { !first().read && completionRecord().visual?.id?.let { it != firstWriter } == true }
        assertEquals(1L, completionRecord().intentRevision)
        list.performScrollToIndex(0)
        list.performScrollToIndex(pages.size + 1)
        compose.waitForIdle()
        assertFalse(first().read)
        assertEquals(2, receipts())

        io {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters WHEN OLD.id=1 " +
                    "BEGIN SELECT RAISE(ABORT,'visual completion write failure'); END"
            )
        }
        approachEnd()
        waitText(error)
        val retryWriter = requireNotNull(completionRecord().visual).id
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        requireNotNull(activity).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText(error)
        assertEquals(retryWriter, completionRecord().visual?.id)
        assertFalse(first().read)
        assertEquals(2, receipts())
        snapshot("visual-completion-retry")
        io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER integration_write_fault") }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        compose.waitUntil(10_000) { first().read }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(error).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithContentDescription(markUnread).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(at, first().firstReadAt)
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(3, receipts())
        snapshot("visual-completion-passed")
        back()
        assertNull(completionRecord().visual)
    }

    @Test fun manualReadFailureRetriesExactCommandAndUnreadSurvivesReopen() = journey("manual-retry") {
        openFirst(); showReaderActions()
        io { db.openHelper.writableDatabase.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters WHEN OLD.id=1 BEGIN SELECT RAISE(ABORT,'completion write failure'); END") }
        compose.onNodeWithContentDescription(context.getString(R.string.reader_mark_read)).performClick()
        waitText(context.getString(R.string.reader_completion_save_error))
        assertFalse(first().read); assertNull(first().firstReadAt); assertEquals(0, receipts())
        assertEquals(0L, completionRecord().intentRevision)
        snapshot("manual-retry-error")
        io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER integration_write_fault") }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        compose.waitUntil(10_000) { first().read }
        val firstReadAt = requireNotNull(first().firstReadAt)
        assertEquals(0L, firstReadAt % 1000); assertEquals(1L, completionRecord().intentRevision); assertEquals(1, receipts())
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(context.getString(R.string.reader_mark_unread)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.reader_mark_unread)).performClick()
        compose.waitUntil(10_000) { !first().read }
        assertEquals(firstReadAt, first().firstReadAt); assertEquals(2L, completionRecord().intentRevision); assertEquals(2, receipts())
        assertEquals(0f, first().readProgress, 0f); assertNull(checkpoint())
        snapshot("manual-retry-passed")
        back(); activity!!.close(); activity = null; openFirst()
        assertFalse(first().read); assertEquals(firstReadAt, first().firstReadAt); assertEquals(2, receipts())
        back()
    }

    @Test fun librarySelectedNovelsUseGuardedCompletion() = journey("library-selected-novels") {
        val secondTitle = "Second selected book"
        val chapterDao = db.chapterDao()
        val sqlite = db.openHelper.writableDatabase
        val otherChapter = ChapterEntity(id = 5, novelId = 3, url = "/unselected", name = "Unselected chapter")
        io {
            db.novelDao().upsert(NovelEntity(id = 2, sourceId = LOCAL_SOURCE_ID, url = "local://second-selected", title = secondTitle, favorite = true))
            db.novelDao().upsert(NovelEntity(id = 3, sourceId = LOCAL_SOURCE_ID, url = "local://unselected", title = "Unselected book", favorite = true))
            chapterDao.insertAll(listOf(
                ChapterEntity(id = 3, novelId = 2, url = "/second/first", name = "Another first chapter", readProgress = 0.375f),
                ChapterEntity(id = 4, novelId = 2, url = "/second/second", name = "Another second chapter", firstReadAt = 42),
                otherChapter,
            ))
            sqlite.execSQL(
                "CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters WHEN OLD.id=4 " +
                    "BEGIN SELECT RAISE(ABORT,'later selected novel failed'); END"
            )
        }
        val secondBefore = io { chapterDao.getChaptersOnce(2) }
        val retry = context.getString(R.string.action_retry)
        val pairSelected = context.resources.getQuantityString(R.plurals.selection_count, 2, 2)

        fun selectPair() {
            compose.onNodeWithText("Integration book").performTouchInput { longClick() }
            compose.onNodeWithText(secondTitle).performClick()
            waitText(pairSelected)
        }

        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitText("Integration book")
        waitText(secondTitle)
        selectPair()
        compose.onNodeWithText(context.getString(R.string.library_mark_read)).performClick()

        waitText(retry)
        val firstCommitted = io { chapterDao.getChaptersOnce(1) }
        assertTrue(firstCommitted.all { it.read && it.firstReadAt != null })
        assertEquals(secondBefore, io { chapterDao.getChaptersOnce(2) })
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(1, receipts())
        assertEquals(listOf(otherChapter), io { chapterDao.getChaptersOnce(3) })

        io {
            sqlite.execSQL("DROP TRIGGER integration_write_fault")
            sqlite.execSQL(
                "CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters WHEN OLD.novelId=1 " +
                    "BEGIN SELECT RAISE(ABORT,'confirmed novel must not be written on retry'); END"
            )
        }
        compose.onNodeWithText(retry).performClick()
        compose.waitUntil(10_000) { io { chapterDao.getChaptersOnce(2).all { it.read } } }
        assertEquals(firstCommitted, io { chapterDao.getChaptersOnce(1) })
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(1, receipts())
        val secondCommitted = io { chapterDao.getChaptersOnce(2) }
        val secondFirstRead = requireNotNull(secondCommitted.single { it.id == 3L }.firstReadAt)
        assertEquals(
            secondBefore.map { it.copy(read = true, firstReadAt = it.firstReadAt ?: secondFirstRead) },
            secondCommitted,
        )
        assertEquals(listOf(otherChapter), io { chapterDao.getChaptersOnce(3) })

        io { sqlite.execSQL("DROP TRIGGER integration_write_fault") }
        selectPair()
        compose.onNodeWithText(context.getString(R.string.library_mark_unread)).performClick()
        compose.waitUntil(10_000) { io { (chapterDao.getChaptersOnce(1) + chapterDao.getChaptersOnce(2)).none { it.read } } }
        assertEquals(firstCommitted.map { it.copy(read = false) }, io { chapterDao.getChaptersOnce(1) })
        assertEquals(secondCommitted.map { it.copy(read = false) }, io { chapterDao.getChaptersOnce(2) })
        assertEquals(2L, completionRecord().intentRevision)
        assertEquals(2, receipts())
        assertEquals(listOf(otherChapter), io { chapterDao.getChaptersOnce(3) })
        snapshot("library-selected-novels-passed")
    }

    @Test fun partialWordCountRetryPreservesLaterExplicitUnread() = journey("completion-partial") {
        io {
            db.chapterDao().insertAll(listOf(ChapterEntity(id=3, novelId=1, url="/integration/backfill", name="Backfill chapter",
                chapterNumber=3f, read=true, firstReadAt=42, downloadStatus=2)))
            db.chapterDao().upsertContent(ChapterContentEntity(3, encodeChapterContent(pages)))
        }
        openFirst(); showReaderActions()
        io { db.openHelper.writableDatabase.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE OF wordCount ON chapters WHEN OLD.id=3 BEGIN SELECT RAISE(ABORT,'word count failure'); END") }
        compose.onNodeWithContentDescription(context.getString(R.string.reader_mark_read)).performClick()
        waitText(context.getString(R.string.reader_completion_partial_error))
        assertTrue(first().read); val acceptedAt = first().firstReadAt
        assertEquals(1L, completionRecord().intentRevision); assertEquals(1, receipts())
        snapshot("completion-partial-error")
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_mark_unread)).performClick()
        waitText(context.getString(R.string.reader_completion_partial_error))
        assertFalse(first().read); assertEquals(acceptedAt, first().firstReadAt)
        assertEquals(2L, completionRecord().intentRevision); assertEquals(2, receipts())
        io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER integration_write_fault") }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        compose.waitUntil(10_000) { io { db.chapterDao().getByUrl(1,"/integration/backfill")!!.wordCount > 0 } }
        compose.waitForIdle()
        assertFalse(first().read); assertEquals(acceptedAt, first().firstReadAt)
        assertEquals(2L, completionRecord().intentRevision); assertEquals(2, receipts())
        compose.onNodeWithContentDescription(context.getString(R.string.reader_mark_read)).assertExists()
        assertEquals(0f, first().readProgress, 0f); assertNull(checkpoint())
        snapshot("completion-partial-passed"); back()
    }

    @Test fun detailSelectedChaptersUseGuardedCompletion() = journey("detail-selected-chapters") {
        val unselected = ChapterEntity(id = 3, novelId = 1, url = "/integration/unselected", name = "Unselected chapter",
            chapterNumber = 3f, read = true, firstReadAt = 42)
        io {
            val chapters = db.chapterDao()
            val second = requireNotNull(chapters.getByUrl(1, "/integration/second"))
            chapters.upsertAll(listOf(second.copy(read = true, firstReadAt = 24, readProgress = 0.5f)))
            chapters.insertAll(listOf(unselected))
        }
        fun chapterRows() = io { db.chapterDao().getChapterMetadataOnce(1) }

        openDetail()
        val before = chapterRows()
        val expected = before.map { if (it.id == 2L) it.copy(read = false) else it }

        compose.onNodeWithText(firstName).performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithContentDescription(context.getString(R.string.action_select_all)).assertExists()
        compose.onNodeWithText(secondName).performScrollTo().performClick()
        val sql = db.openHelper.writableDatabase
        io { sql.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters WHEN NEW.id=2 BEGIN SELECT RAISE(ABORT,'selected chapter write failure'); END") }
        val markUnread = context.getString(R.string.novel_mark_unread)
        compose.onNode(hasText(markUnread) and hasClickAction()).performClick()

        val saveError = context.getString(R.string.reader_completion_save_error)
        waitText(saveError)
        assertEquals(before, chapterRows())
        assertEquals(0, receipts())
        snapshot("detail-selected-chapters-save-error")

        io { sql.execSQL("DROP TRIGGER integration_write_fault") }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        compose.waitUntil(10_000) { chapterRows() == expected }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(saveError).fetchSemanticsNodes().isEmpty() }
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(1, receipts())
        assertNull(checkpoint())
        snapshot("detail-selected-chapters-passed")
    }

    @Test fun detailMarkAllRetriesAtomicWriteThenRetriesOnlyWordCount() = journey("detail-mark-all") {
        val hidden = ChapterEntity(id = 3, novelId = 1, url = "/integration/locked", name = "Locked chapter",
            chapterNumber = 3f, locked = true, readProgress = 0.25f, readAnchorItemIndex = 4, readAnchorItemOffset = 12)
        val unrelated = hidden.copy(id = 4, novelId = 2, url = "/integration/other")
        io {
            db.novelDao().upsert(NovelEntity(id = 2, sourceId = LOCAL_SOURCE_ID, url = "local://other", title = "Other"))
            db.chapterDao().insertAll(listOf(hidden, unrelated))
        }
        val sql = db.openHelper.writableDatabase
        fun selectedRows() = io { db.chapterDao().getChapterMetadataOnce(1) }
        fun menuAction(action: Int) {
            compose.onNodeWithContentDescription(context.getString(R.string.action_more_actions)).performClick()
            compose.onNodeWithText(context.getString(action)).performClick()
        }
        openDetail()
        val before = selectedRows()
        io { sql.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters WHEN NEW.id=2 BEGIN SELECT RAISE(ABORT,'atomic bulk failure'); END") }
        menuAction(R.string.novel_mark_all_read)
        waitText(context.getString(R.string.reader_completion_save_error))
        assertEquals(before, selectedRows())
        assertEquals(0, receipts())
        snapshot("detail-mark-all-save-error")

        io {
            sql.execSQL("DROP TRIGGER integration_write_fault")
            sql.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE OF wordCount ON chapters WHEN NEW.id=1 BEGIN SELECT RAISE(ABORT,'bulk word count failure'); END")
        }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        waitText(context.getString(R.string.reader_completion_partial_error))
        val committed = selectedRows()
        val firstReadAt = requireNotNull(committed.first().firstReadAt)
        assertEquals(0L, firstReadAt % 1000)
        assertEquals(before.map { it.copy(read = true, firstReadAt = firstReadAt) }, committed)
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(1, receipts())
        snapshot("detail-mark-all-partial-error")

        io {
            sql.execSQL("DROP TRIGGER integration_write_fault")
            sql.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters BEGIN SELECT RAISE(ABORT,'unexpected read rewrite'); END")
        }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        compose.waitUntil(10_000) { selectedRows().take(2).all { it.wordCount > 0 } }
        compose.waitForIdle()
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(1, receipts())
        val backfilled = selectedRows()
        io { sql.execSQL("DROP TRIGGER integration_write_fault") }
        menuAction(R.string.novel_mark_all_unread)
        compose.waitUntil(10_000) { selectedRows().none { it.read } }
        assertEquals(backfilled.map { it.copy(read = false) }, selectedRows())
        assertEquals(listOf(unrelated), io { db.chapterDao().getChapterMetadataOnce(2) })
        assertEquals(2L, completionRecord().intentRevision)
        assertEquals(2, receipts())
        assertNull(checkpoint())
        snapshot("detail-mark-all-passed")
    }

    @Test fun updatesSelectedEntriesUseGuardedCompletion() = journey("updates-selected-completion") {
        val otherNovelUrl = "local://updates-other"
        val crossNovel = ChapterEntity(
            id = 3, novelId = 2, url = "/updates/cross-novel", name = "Cross-novel chapter",
            readProgress = 0.25f, readAnchorItemIndex = 3, readAnchorItemOffset = 8,
        )
        val missingUrl = "/updates/missing"
        val laterChapter = ChapterEntity(id = 4, novelId = 1, url = missingUrl, name = "Chapter added after selection")
        val selectedLabel = "Selected chapter entry"
        val duplicateLabel = "Duplicate chapter entry"
        val missingLabel = "Unavailable chapter entry"
        val otherLabel = "Selected entry in another novel"
        val foundAt = System.currentTimeMillis()
        fun entry(id: Long, url: String, label: String, novelId: Long = 1) = LibraryUpdateEntity(
            id = id,
            novelId = novelId,
            sourcePackage = LOCAL_PKG,
            novelUrl = if (novelId == 1L) novelUrl else otherNovelUrl,
            novelTitle = if (novelId == 1L) "Integration book" else "Other updates novel",
            chapterUrl = url,
            chapterName = label,
            foundAt = foundAt - id * 1000,
        )
        val unselected = io {
            db.novelDao().upsert(
                NovelEntity(id = 2, sourceId = LOCAL_SOURCE_ID, url = otherNovelUrl, title = "Other updates novel"),
            )
            db.chapterDao().insertAll(listOf(crossNovel))
            val second = requireNotNull(db.chapterDao().getByUrl(1, "/integration/second"))
            db.libraryUpdateDao().insertAll(listOf(
                entry(1, key.url, selectedLabel),
                entry(2, key.url, duplicateLabel),
                entry(3, missingUrl, missingLabel),
                entry(4, crossNovel.url, otherLabel, novelId = 2),
                entry(5, second.url, "Unselected chapter entry"),
            ))
            second
        }
        val untouchedFirst = first()
        fun secondNovelChapter() = io { requireNotNull(db.chapterDao().getByUrl(2, crossNovel.url)) }
        activity = ActivityScenario.launch(
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_NAV_TARGET, NAV_TARGET_UPDATES),
        )
        waitText(selectedLabel)
        val list = compose.onNode(
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        )
        fun select(label: String, begin: Boolean = false) {
            list.performScrollToNode(hasText(label))
            val row = compose.onNodeWithText(label)
            if (begin) row.performTouchInput { longClick(center) } else row.performClick()
        }
        val markRead = context.getString(R.string.library_mark_read)
        val markUnread = context.getString(R.string.library_mark_unread)
        val error = context.getString(R.string.reader_completion_save_error)
        fun markSelection(label: String) {
            val action = hasText(label) and hasClickAction()
            compose.waitUntil(10_000) { compose.onAllNodes(action).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(action).performClick()
        }

        io {
            db.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER integration_write_fault BEFORE UPDATE OF read ON chapters
                    WHEN OLD.id=3 BEGIN SELECT RAISE(ABORT,'updates batch write failure'); END""",
            )
        }
        select(selectedLabel, begin = true)
        select(duplicateLabel)
        select(missingLabel)
        select(otherLabel)
        markSelection(markRead)
        waitText(error)
        assertEquals(untouchedFirst, first())
        assertEquals(crossNovel, secondNovelChapter())
        assertEquals(0, receipts())
        snapshot("updates-selected-completion-error")

        io {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER integration_write_fault")
            db.chapterDao().insertAll(listOf(laterChapter))
        }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        compose.waitUntil(10_000) { first().read && secondNovelChapter().read }
        val at = requireNotNull(first().firstReadAt)
        val otherAt = requireNotNull(secondNovelChapter().firstReadAt)
        assertEquals(1L, completionRecord().intentRevision)
        assertEquals(1, receipts())
        assertEquals(crossNovel.copy(read = true, firstReadAt = otherAt), secondNovelChapter())
        assertEquals(laterChapter, io { db.chapterDao().getByUrl(1, missingUrl) })
        assertNull(io { db.readingStateStorageDao().find(laterChapter.id) })
        assertEquals(unselected, io { db.chapterDao().getByUrl(1, unselected.url) })
        compose.waitUntil(10_000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isEmpty() }

        select(selectedLabel, begin = true)
        select(otherLabel)
        markSelection(markUnread)
        compose.waitUntil(10_000) { !first().read && !secondNovelChapter().read }
        assertEquals(at, first().firstReadAt)
        assertEquals(crossNovel.copy(firstReadAt = otherAt), secondNovelChapter())
        assertEquals(2L, completionRecord().intentRevision)
        assertEquals(2, receipts())
        assertEquals(unselected, io { db.chapterDao().getByUrl(1, unselected.url) })
        compose.waitForIdle()
        snapshot("updates-selected-completion-passed")
    }

    @Test fun failedCheckpointSaveCanBeDismissedAndRetriedWithoutLosingInput() = journey("retry") {
        openFirst()
        io { db.openHelper.writableDatabase.execSQL("CREATE TRIGGER integration_write_fault BEFORE UPDATE ON reading_checkpoints BEGIN SELECT RAISE(ABORT,'integration write failure'); END") }
        swipe()
        waitText(context.getString(R.string.reader_position_save_error))
        assertNull(checkpoint())
        snapshot("retry-error")
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        waitText("Integration paragraph", true)
        io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER integration_write_fault") }
        back()
        assertNotNull(checkpoint()); assertTrue(first().readProgress > 0f); assertFalse(first().read)
        snapshot("retry-passed")
    }
}
