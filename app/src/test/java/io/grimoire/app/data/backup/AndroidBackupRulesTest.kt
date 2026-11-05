package io.grimoire.app.data.backup

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class AndroidBackupRulesTest {
    @Test fun `all transports allow only durable user data`() {
        val xmlDir = File("src/main/res/xml")
        val factory = DocumentBuilderFactory.newInstance()
        val legacy = factory.newDocumentBuilder().parse(File(xmlDir, "backup_rules.xml"))
        val modern = factory.newDocumentBuilder().parse(File(xmlDir, "data_extraction_rules.xml"))
        val transports = listOf(legacy.documentElement) + listOf("cloud-backup", "device-transfer").map {
            modern.getElementsByTagName(it).item(0) as Element
        }
        transports.forEach { transport ->
            val includes = transport.getElementsByTagName("include")
            val paths = (0 until includes.length).map {
                val element = includes.item(it) as Element
                element.getAttribute("domain") to element.getAttribute("path")
            }.toSet()
            assertEquals(setOf("database" to ".", "file" to "covers/", "file" to "epub_covers/"), paths)
            // The includes are an allowlist, and lint fails release builds on an
            // exclude outside an included path.
            assertEquals(0, transport.getElementsByTagName("exclude").length)
        }
    }
}
