package io.grimoire.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.jar.JarInputStream

/** Installs the packaged reader fixture from a signed repository entirely through the Desktop UI. */
class ExtensionInstallUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun installFromSignedRepositoryBrowseRestartOfflineAndRemove() {
        val platform = resetSharedFixturePlatform()
        val repository = signedRepository()
        val data = temporary.newFolder().toPath()
        var model = open(data, repository, platform)
        val active = mutableStateOf(model)
        fun settled() {
            compose.waitUntil(15_000) { !active.value.state.value.busy }
            compose.waitForIdle()
            assertNull(active.value.state.value.error, active.value.state.value.error)
        }
        fun click(label: String) { compose.onNode(hasText(label) and hasClickAction()).performClick(); settled() }
        fun scrollClick(label: String) { compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick(); settled() }
        try {
            compose.setContent { key(active.value) {
                MaterialTheme { Surface(Modifier.size(1100.dp, 760.dp)) { DesktopReader(active.value, false, null) {} } }
            } }
            click("Extensions")
            compose.onNodeWithText("Reader fixture").assertIsDisplayed()
            compose.onNodeWithText("No extensions installed yet. Install one from the list below.").assertIsDisplayed()
            scrollClick("Install")
            compose.onNode(hasText("Remove") and hasClickAction()).assertExists()
            assertEquals(listOf("Reader fixture"), model.state.value.sources.map { it.name })
            capture("extension-installed.png")
            click("Browse")
            val title = model.state.value.novels.first().title
            compose.onNodeWithText(title).assertIsDisplayed()

            // Restart offline: the installed source loads from the local store without any request.
            runBlocking { model.close() }
            platform.allowed.set(false)
            model = open(data, repository, platform)
            model.offline(true)
            compose.runOnIdle { active.value = model }
            assertEquals(listOf("Reader fixture"), model.state.value.sources.map { it.name })
            click("Browse")
            compose.onNodeWithText("Go online to browse this source.").assertIsDisplayed()
            assertTrue(repository.requests.none { it.endsWith(".jar") || it.endsWith(".json") })

            click("Extensions")
            scrollClick("Remove")
            assertTrue(model.state.value.sources.isEmpty())
            click("Browse")
            compose.onNodeWithText("Install an extension to browse its catalog.").assertIsDisplayed()
        } catch (failure: Throwable) {
            runCatching { capture("extension-failure.png") }; throw failure
        } finally { runBlocking { active.value.close() } }
    }

    private fun open(data: Path, repository: SignedRepository, platform: ReaderFixturePlatform): ReaderModel {
        repository.requests.clear()
        val library = LocalLibrary(data)
        val manager = ExtensionManager(library, "1.6.0", repository, listOf(repository.trust))
        return desktopReaderModel(library, "1.6.0", platform.allowed, manager)
    }

    private class SignedRepository(val trust: RepositoryTrust, val files: Map<String, ByteArray>) : ArtifactFetcher {
        val requests = mutableListOf<String>()
        var online = true
        override fun fetch(url: String, limit: Long): ByteArray {
            requests += url
            if (!online) throw IOException("Offline mode is enabled")
            return files[url]?.also { if (it.size > limit) throw IOException("too large") } ?: throw IOException("HTTP 404 for $url")
        }
    }

    private fun signedRepository(): SignedRepository {
        val jar = Files.readAllBytes(Path.of(checkNotNull(System.getProperty("desktop.readerFixtureJar"))))
        val attributes = JarInputStream(jar.inputStream()).use { it.manifest.mainAttributes }
        fun attribute(name: String) = checkNotNull(attributes.getValue("Grimoire-$name"))
        val pkg = attribute("Source-Package")
        val file = "$pkg-${attribute("Version-Name")}.jar"
        val index = JSONObject().put("format", 1).put("repository", "io.grimoire.uitest").put("sequence", 1)
            .put("libraries", JSONArray())
            .put("extensions", JSONArray().put(JSONObject()
                .put("pkg", pkg).put("name", "Reader fixture").put("lang", "en")
                .put("sourceClass", attribute("Source-Class"))
                .put("versionCode", attribute("Version-Code").toInt()).put("versionName", attribute("Version-Name"))
                .put("sourceVersionCode", attribute("Source-Version-Code").toInt()).put("coreApi", attribute("Core-Api"))
                .put("hostLibraries", JSONArray(attribute("Host-Libraries").split(',').map(String::trim).filter(String::isNotEmpty)))
                .put("file", file).put("sha256", hex(MessageDigest.getInstance("SHA-256").digest(jar))).put("size", jar.size)))
            .toString().toByteArray()
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val signature = Signature.getInstance("Ed25519").run { initSign(keys.private); update(index); sign() }
        val url = "https://repo.invalid/uitest/jar-index.json"
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded.takeLast(32).toByteArray())
        return SignedRepository(RepositoryTrust("io.grimoire.uitest", url, publicKey), mapOf(
            url to index,
            "$url.sig" to Base64.getEncoder().encode(signature),
            "https://repo.invalid/uitest/$file" to jar,
        ))
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun capture(name: String) {
        val directory = Path.of(checkNotNull(System.getProperty("desktop.ui.evidence")))
        Files.createDirectories(directory)
        val pixels = compose.onRoot().captureToImage().asSkiaBitmap()
        Image.makeFromBitmap(pixels).use { image ->
            image.encodeToData()!!.use { Files.write(directory.resolve(name), it.bytes) }
        }
    }
}
