package io.grimoire.app.integration

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
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
import io.grimoire.app.ui.tour.TourId
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.CheckpointCommand
import io.grimoire.reading.storage.CheckpointCommit
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

    @Before fun seedOwnedEmulator() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("integrationFixtures") == "true") { "Use the dedicated disposable-emulator runner" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Integration fixtures require an emulator" }
        assertEquals(args.getString("integrationSourceSha"), BuildConfig.GIT_SHA)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Run these local-content journeys with emulator networking disabled", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
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
    private fun openFirst() {
        val route = "novel?pkg=$LOCAL_PKG&url=${Uri.encode(novelUrl)}&sourceId=$LOCAL_SOURCE_ID"
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_NAV_TARGET, route))
        waitText(firstName)
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
        assertFalse(io { requireNotNull(db.chapterDao().getByUrl(1, "/integration/second")).read })
        snapshot("completion-passed")
        back()
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
