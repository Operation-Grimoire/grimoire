package io.grimoire.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.network.SourceNetwork
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import io.grimoire.api.model.novel.PageContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path

class ReaderUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun actualScrollInputSavesCheckpointAndRestartRestoresItWithoutAnInitialLayoutRewrite() {
        val fixtureKey = ChapterKey(NovelKey(17, "/checkpoint-book"), "/chapter")
        val fixtureNovel = io.grimoire.api.model.novel.Novel(fixtureKey.novel.url, "Checkpoint book", io.grimoire.api.model.lang.Language.EN)
        val fixtureChapters = listOf(io.grimoire.api.model.novel.Chapter(fixtureKey.url, "Local chapter"))
        val directory = temporary.newFolder().toPath()
        val paragraphs = (0 until 40).map { index -> io.grimoire.api.model.novel.NovelPage(index,
            PageContent.Text("Paragraph $index. " + "The harbor bell rang while Mira followed the light. ".repeat(6))) }
        var library = LocalLibrary(directory).apply {
            remember(fixtureKey.novel, fixtureNovel, fixtureChapters); favorite(fixtureKey.novel, true)
            runBlocking {
                val downloads = DownloadRepository(DesktopDownloadDao(this@apply))
                assertTrue(downloads.commit(requireNotNull(downloads.claim(fixtureKey)), paragraphs) is DownloadCommit.Committed)
            }
        }
        var model = desktopReaderModel(library, "1.6.0", java.util.concurrent.atomic.AtomicBoolean(false))
        model.offline(true)
        val active = mutableStateOf(model)
        try {
            compose.setContent { key(active.value) {
                MaterialTheme { Surface(Modifier.size(1100.dp, 760.dp)) { DesktopReader(active.value, false, null) {} } }
            } }
            compose.onNodeWithText(fixtureNovel.title).performClick(); settled(model)
            compose.onNodeWithText(fixtureChapters.first().name).performClick(); settled(model)
            assertNull(runBlocking { DesktopReadingCheckpointDao(library).read(fixtureKey) })
            compose.onNodeWithTag("reader-body").performMouseInput { moveTo(center); scroll(800f) }
            compose.waitUntil(5_000) { runBlocking { DesktopReadingCheckpointDao(library).read(fixtureKey) } != null }
            val saved = runBlocking { DesktopReadingCheckpointDao(library).read(fixtureKey)!! }
            val ordinal = (saved.position as io.grimoire.reading.anchor.AnchorPosition.Block).ordinal
            assertTrue(ordinal > 0)
            capture("checkpoint-input.png")
            runBlocking { model.close() }
            library = LocalLibrary(directory)
            model = desktopReaderModel(library, "1.6.0", java.util.concurrent.atomic.AtomicBoolean(false)); model.offline(true)
            compose.runOnIdle { active.value = model }
            compose.onNodeWithText(fixtureNovel.title).performClick(); settled(model)
            compose.onNodeWithText("Continue reading").performClick(); settled(model)
            assertEquals(ordinal, model.state.value.checkpointLoad!!.restore.viewport.index)
            assertEquals(0, model.state.value.checkpointLoad!!.restore.viewport.offset)
            compose.onNodeWithText((paragraphs[ordinal].content as PageContent.Text).text).assertIsDisplayed()
            runBlocking { model.flushPosition() }
            assertEquals(saved, runBlocking { DesktopReadingCheckpointDao(library).read(fixtureKey) })
            capture("checkpoint-restored.png")
        } catch (failure: Throwable) {
            runCatching { capture("checkpoint-failure.png") }; throw failure
        } finally { runBlocking { model.close() } }
    }

    @Test fun readerControlsSaveAndReopenChapterOffline() {
        val platform = ReaderFixturePlatform()
        val allowed = platform.allowed
        val requests = platform.requests
        SourceNetwork.install(platform)
        val data = temporary.newFolder().toPath()
        var model = desktopReaderModel(LocalLibrary(data), "1.5.0", allowed)
        val active = mutableStateOf(model)
        val manifest = readerFixtureManifest(temporary.newFolder().toPath())
        fun click(label: String) {
            compose.onNode(hasText(label) and hasClickAction()).performClick()
            settled(model)
        }
        try {
            compose.setContent {
                key(active.value) {
                    MaterialTheme {
                        Surface(Modifier.size(1100.dp, 760.dp)) {
                            DesktopReader(active.value, false, null) { active.value.load(manifest) }
                        }
                    }
                }
            }
            compose.onNode(hasText("Library") and hasClickAction()).assertIsSelected()
            click("Browse")
            compose.onNodeWithText("Browse sources").assertIsDisplayed()
            capture("desktop-shell.png")
            click("Manage extensions")
            compose.onNodeWithText("No extension open").assertIsDisplayed()
            click("Open local extension")
            compose.onNodeWithText("Reader fixture").assertIsDisplayed()
            compose.onNode(hasText("Extensions") and hasClickAction()).assertIsSelected()
            click("Browse")
            val novel = model.state.value.novels.first()
            compose.onNodeWithText(novel.title).performClick()
            settled(model)
            val chapter = checkNotNull(model.state.value.book).chapters.first { !it.locked }
            compose.onNodeWithText(chapter.name).performScrollTo().performClick()
            settled(model)
            val prose = model.state.value.pages.mapNotNull { (it.content as? PageContent.Text)?.text }.first { it.isNotBlank() }
            compose.onNodeWithText(prose).assertIsDisplayed()
            assertNull(model.state.value.observedAnchor)
            assertNotNull(model.state.value.projection!!.document)
            capture("reader.png")
            compose.onNodeWithText("Offline").performClick()
            compose.onNode(hasText(chapter.name) and hasClickAction()).assertIsNotEnabled()
            compose.onNodeWithText("Save chapter offline").performClick()
            settled(model)
            compose.onNode(hasText(chapter.name) and hasClickAction()).assertIsEnabled()
            val count = requests.get()
            click("Back")
            compose.onNodeWithText("Continue reading").assertIsDisplayed()
            compose.onNode(hasText("Browse") and hasClickAction()).assertIsSelected()
            click("Back")
            compose.onNodeWithText("Go online to browse this source.").assertIsDisplayed()
            click("Settings")
            compose.onNodeWithText("Offline").assertIsOn().performClick()
            click("Browse")
            compose.onNodeWithText(novel.title).assertIsDisplayed()
            assertEquals(count, requests.get())
            runBlocking { model.close() }
            allowed.set(false)
            model = desktopReaderModel(LocalLibrary(data), "1.5.0", allowed)
            model.offline(true)
            compose.runOnIdle { active.value = model }
            assertNull(model.state.value.sourceName)
            compose.onNodeWithText(novel.title).performClick()
            settled(model)
            compose.onNodeWithText("Continue reading").performClick()
            settled(model)
            compose.onNodeWithText(prose).assertIsDisplayed()
            assertNull(model.state.value.observedAnchor)
            assertEquals(io.grimoire.reading.ChapterKey(model.state.value.book!!.key, chapter.url), model.state.value.projection!!.document!!.key)
            capture("offline-restart.png")
            click("Back")
            click("Back")
            compose.onNode(hasText("Library") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText(novel.title).assertIsDisplayed()
            assertEquals(count, requests.get())
            assertEquals(4, count) // fixed test extension: catalog, details, chapter list and chapter
        } catch (failure: Throwable) {
            runCatching { capture("failure.png") }
            throw failure
        } finally { runBlocking { model.close() } }
    }

    @Test fun historyControlsKeepRecordingOptInAndClearWithoutLosingResume() {
        val novelKey = NovelKey(17, "/history-book")
        val novel = Novel(novelKey.url, "History fixture", Language.EN)
        val chapter = Chapter("/first", "History first")
        val chapterKey = ChapterKey(novelKey, chapter.url)
        val text = "Mira followed the harbor lights."
        val directory = temporary.newFolder().toPath()
        val library = LocalLibrary(directory).apply {
            remember(novelKey, novel, listOf(chapter))
            favorite(novelKey, true)
            runBlocking {
                val downloads = DownloadRepository(DesktopDownloadDao(this@apply))
                val pages = listOf(NovelPage(0, PageContent.Text(text)))
                assertTrue(downloads.commit(requireNotNull(downloads.claim(chapterKey)), pages) is DownloadCommit.Committed)
            }
        }
        val model = desktopReaderModel(library, "1.6.0", java.util.concurrent.atomic.AtomicBoolean(false))
        model.offline(true)
        fun click(label: String) {
            compose.onNode(hasText(label) and hasClickAction()).performClick()
            settled(model)
        }
        fun openFromLibrary() {
            click("Library")
            click(novel.title)
            compose.onNode(hasText(chapter.name) and hasClickAction()).performClick()
            settled(model)
        }
        try {
            compose.setContent {
                MaterialTheme { Surface(Modifier.size(1100.dp, 760.dp)) { DesktopReader(model, false, null) {} } }
            }
            openFromLibrary()
            click("History")
            val record = compose.onNodeWithText("Record opened chapters")
            record.assertIsOff()
            compose.onNodeWithText("No reading history yet.").assertIsDisplayed()

            click("Record opened chapters")
            record.assertIsOn()
            openFromLibrary()
            click("History")
            compose.onNodeWithText(chapter.name).assertIsDisplayed()
            capture("history-controls.png")
            click("Open chapter")
            compose.onNodeWithText(text).assertIsDisplayed()
            click("Back")
            compose.onNodeWithText("Continue reading").assertIsDisplayed()
            click("Back")
            compose.onNode(hasText("History") and hasClickAction()).assertIsSelected()
            click("Remove")
            compose.onNodeWithText("No reading history yet.").assertIsDisplayed()

            openFromLibrary()
            click("History")
            click("Record opened chapters")
            record.assertIsOff()
            compose.onNodeWithText(chapter.name).assertIsDisplayed()
            click("Clear history")
            click("Clear")
            compose.onNodeWithText("No reading history yet.").assertIsDisplayed()
            assertEquals(chapter.url, library.book(novelKey)?.resumeChapterUrl)
        } catch (failure: Throwable) {
            runCatching { capture("history-failure.png") }
            throw failure
        } finally {
            runBlocking { model.close() }
        }
        val reopened = LocalLibrary(directory)
        val restarted = desktopReaderModel(reopened, "1.6.0", java.util.concurrent.atomic.AtomicBoolean(false))
        try {
            assertFalse(restarted.state.value.historyRecording)
            restarted.showHistory()
            settled(restarted)
            assertTrue(restarted.state.value.history.isEmpty())
            assertEquals(chapter.url, reopened.book(novelKey)?.resumeChapterUrl)
            assertNotNull(reopened.downloaded(chapterKey))
        } finally {
            runBlocking { restarted.close() }
        }
    }

    private fun settled(model: ReaderModel) {
        compose.waitUntil(15_000) { !model.state.value.busy }
        compose.waitForIdle()
        assertNull(model.state.value.error, model.state.value.error)
    }

    private fun capture(name: String) {
        val directory = Path.of(checkNotNull(System.getProperty("desktop.ui.evidence")))
        Files.createDirectories(directory)
        val pixels = compose.onRoot().captureToImage().asSkiaBitmap()
        Image.makeFromBitmap(pixels).use { image ->
            image.encodeToData()!!.use { Files.write(directory.resolve(name), it.bytes) }
        }
    }
}
