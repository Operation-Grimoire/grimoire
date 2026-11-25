package io.grimoire.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Keeps every shipped UI language in step with the English resources and the locale lists. */
class LocaleCoverageTest {

    private val res = File("src/main/res")

    // Strings used by Compose UI shared with Desktop; same folder qualifiers as the app.
    private val sharedRes = File("../shared-ui/src/commonMain/composeResources")

    private val translations: List<File> = translationsIn(res)

    private fun translationsIn(root: File): List<File> = root.listFiles { f ->
        f.isDirectory && f.name.startsWith("values-") && f.listFiles { x -> x.name.endsWith("strings.xml") }!!.isNotEmpty()
    }!!.sortedBy { it.name }

    @Test
    fun `every translation has exactly the english keys`() {
        val english = entries(File(res, "values"))
        assertTrue(translations.isNotEmpty())
        for (dir in translations) {
            val translated = entries(dir)
            assertEquals("missing in ${dir.name}", emptySet<String>(), english.keys - translated.keys)
            assertEquals("stale in ${dir.name}", emptySet<String>(), translated.keys - english.keys)
        }
    }

    @Test
    fun `shared UI strings cover the same languages with exactly the english keys`() {
        val english = entries(File(sharedRes, "values"))
        val shared = translationsIn(sharedRes)
        assertTrue(english.isNotEmpty())
        assertEquals(translations.map { it.name }, shared.map { it.name })
        for (dir in shared) {
            val translated = entries(dir)
            assertEquals("missing in shared ${dir.name}", emptySet<String>(), english.keys - translated.keys)
            assertEquals("stale in shared ${dir.name}", emptySet<String>(), translated.keys - english.keys)
            for ((key, text) in translated) assertEquals("shared ${dir.name}/$key", formatArgs(english.getValue(key)), formatArgs(text))
        }
        // A key still used by Android-only code is copied, not moved; both copies must say the same thing.
        for (dir in listOf(File(res, "values")) + translations) {
            val app = entries(dir)
            val copy = entries(File(sharedRes, dir.name))
            for (key in app.keys intersect copy.keys) assertEquals("${dir.name}/$key differs between app and shared", app[key], copy[key])
        }
    }

    @Test
    fun `shared UI compose resources are packaged into merged Android assets`() {
        val mergedAssets = File(
            requireNotNull(System.getProperty("grimoire.mergedAssetsDir")) {
                "grimoire.mergedAssetsDir system property must be provided by :app Gradle config"
            },
        )
        val expectedDirs = listOf("values") + translations.map { it.name }
        for (dir in expectedDirs) {
            val packaged = File(mergedAssets, "composeResources/io.grimoire.ui.resources/$dir/strings.commonMain.cvr")
            assertTrue("Missing packaged Compose resource in Android assets: $packaged", packaged.isFile && packaged.length() > 0)
        }
    }

    @Test
    fun `translations keep the english format arguments`() {
        val english = entries(File(res, "values"))
        for (dir in translations) {
            for ((key, text) in entries(dir)) {
                val expected = english[key] ?: continue
                assertEquals("${dir.name}/$key", formatArgs(expected), formatArgs(text))
            }
        }
    }

    @Test
    fun `locale config and AppLocale list every shipped language`() {
        val shipped = setOf("en") + translations.map { folderTag(it.name) }
        val config = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "xml/locales_config.xml"))
            .getElementsByTagName("locale")
            .let { nodes -> (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") } }
            .toSet()
        assertEquals(shipped, config)
        assertEquals(shipped, AppLocale.supported.filter { it != AppLocale.SYSTEM }.toSet())
    }

    /** Translatable string and plural texts in [dir], keyed by name (plural items as `name#quantity`). */
    private fun entries(dir: File): Map<String, String> {
        val out = mutableMapOf<String, String>()
        dir.listFiles { f -> f.name.endsWith("strings.xml") }!!.forEach { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val strings = root.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val el = strings.item(i) as Element
                if (el.getAttribute("translatable") == "false") continue
                out[el.getAttribute("name")] = el.textContent
            }
            val plurals = root.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val el = plurals.item(i) as Element
                // Quantities differ by language (zh has only "other"), so compare the "other" form.
                val items = el.getElementsByTagName("item")
                val other = (0 until items.length).map { items.item(it) as Element }
                    .firstOrNull { it.getAttribute("quantity") == "other" }
                out[el.getAttribute("name")] = other?.textContent.orEmpty()
            }
        }
        return out
    }

    /** `values-pt-rBR` → `pt-BR`. */
    private fun folderTag(folder: String): String = folder.removePrefix("values-").replace("-r", "-")

    private fun formatArgs(text: String): List<String> =
        FORMAT_ARG.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        val FORMAT_ARG = Regex("""%(\d+\$)?[sdf]""")
    }
}
