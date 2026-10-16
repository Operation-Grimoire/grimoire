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

    private val translations: List<File> = res.listFiles { f ->
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
        val shipped = setOf("en") + translations.map { it.name.removePrefix("values-") }
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

    private fun formatArgs(text: String): List<String> =
        FORMAT_ARG.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        val FORMAT_ARG = Regex("""%(\d+\$)?[sdf]""")
    }
}
