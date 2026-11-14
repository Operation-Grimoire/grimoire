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
import io.grimoire.api.network.SourceHttpPlatform
import io.grimoire.api.network.SourceNetwork
import io.grimoire.api.model.novel.PageContent
import io.grimoire.contract.RoyalRoadContractPlatform
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RoyalRoadUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun readerControlsSaveAndReopenChapterOffline() {
        val allowed = AtomicBoolean(true)
        val requests = AtomicInteger()
        val live = System.getProperty("desktop.ui.live") == "true"
        val platform = if (live) DesktopHttpPlatform(allowed) else RoyalRoadContractPlatform()
        SourceNetwork.install(object : SourceHttpPlatform {
            override val cookieJar = platform.cookieJar
            override val userAgent = platform.userAgent
            override val interceptors = listOf(Interceptor { chain ->
                requests.incrementAndGet()
                check(allowed.get()) { "Unexpected HTTP after offline restart" }
                platform.interceptors.single().intercept(chain)
            })
        })
        val data = temporary.newFolder().toPath()
        var model = ReaderModel(LocalLibrary(data), "1.5.0", allowed)
        val active = mutableStateOf(model)
        try {
            model.load(Path.of(checkNotNull(System.getProperty("desktop.test.manifest"))))
            compose.setContent {
                key(active.value) {
                    MaterialTheme { Surface(Modifier.size(1100.dp, 760.dp)) { DesktopReader(active.value, false, null) {} } }
                }
            }
            settled(model)
            compose.onNodeWithText("Browse").performClick()
            settled(model)
            val novel = model.state.value.novels.first()
            if (!live) assertEquals("The Lantern Keeper", novel.title)
            compose.onNodeWithText(novel.title).performClick()
            settled(model)
            val chapter = checkNotNull(model.state.value.book).chapters.first { !it.locked }
            compose.onNodeWithText(chapter.name).performScrollTo().performClick()
            settled(model)
            val prose = model.state.value.pages.mapNotNull { (it.content as? PageContent.Text)?.text }.first { it.isNotBlank() }
            compose.onNodeWithText(prose).assertIsDisplayed()
            capture("reader.png")
            compose.onNodeWithText("Save chapter offline").performClick()
            settled(model)
            val count = requests.get()
            runBlocking { model.close() }
            allowed.set(false)
            model = ReaderModel(LocalLibrary(data), "1.5.0", allowed)
            model.offline(true)
            compose.runOnIdle { active.value = model }
            compose.onNodeWithText("No extension open").assertIsDisplayed()
            compose.onNodeWithText(novel.title).performClick()
            settled(model)
            compose.onNodeWithText("Continue reading").performClick()
            settled(model)
            compose.onNodeWithText(prose).assertIsDisplayed()
            capture("offline-restart.png")
            assertEquals(count, requests.get())
            assertEquals(4, count)
        } catch (failure: Throwable) {
            runCatching { capture("failure.png") }
            throw failure
        } finally { runBlocking { model.close() } }
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
