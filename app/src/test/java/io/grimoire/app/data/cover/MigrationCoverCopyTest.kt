package io.grimoire.app.data.cover

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MigrationCoverCopyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun migratedCoverSurvivesSourceReplacementAndKeepsOldTargetUntilCommit() {
        val sourceDir = temporary.newFolder("1")
        val source = File(sourceDir, "cover").apply { writeText("source image") }
        val targetDir = temporary.newFolder("2")
        val oldTarget = File(targetDir, "old").apply { writeText("old target image") }
        val copy = copyMigrationCover(source, targetDir)
        assertEquals(targetDir.canonicalFile, copy.parentFile?.canonicalFile)
        assertNotEquals(source.canonicalFile, copy.canonicalFile)
        assertTrue(source.delete())
        assertEquals("source image", copy.readText())
        assertEquals("old target image", oldTarget.readText())
        cleanupMigrationCovers(targetDir, oldTarget.absolutePath, copy.absolutePath, copy.absolutePath)
        assertFalse(oldTarget.exists())
        assertEquals("source image", copy.readText())
    }

    @Test fun rollbackKeepsPreviousCoverAndRemovesOnlyStagedCopy() {
        val source = temporary.newFile("source").apply { writeText("source image") }
        val targetDir = temporary.newFolder("2")
        val oldTarget = File(targetDir, "old").apply { writeText("old target image") }
        val copy = copyMigrationCover(source, targetDir)
        cleanupMigrationCovers(targetDir, oldTarget.absolutePath, copy.absolutePath, oldTarget.absolutePath)
        assertFalse(copy.exists())
        assertEquals("old target image", oldTarget.readText())
        assertEquals("source image", source.readText())
    }

    @Test fun cleanupNeverDeletesAnOutsideCoverPath() {
        val source = temporary.newFile("source").apply { writeText("source image") }
        val targetDir = temporary.newFolder("2")
        val copy = copyMigrationCover(source, targetDir)
        cleanupMigrationCovers(targetDir, source.absolutePath, copy.absolutePath, copy.absolutePath)
        assertEquals("source image", source.readText())
        assertEquals("source image", copy.readText())
    }

    @Test fun failedCopyLeavesTargetCoverAndNoPartialFile() {
        val targetDir = temporary.newFolder("2")
        val oldTarget = File(targetDir, "old").apply { writeText("old image") }
        try {
            copyMigrationCover(File(temporary.root, "missing"), targetDir)
            fail("A missing source must abort migration")
        } catch (_: IOException) {
            assertEquals("old image", oldTarget.readText())
            assertEquals(listOf("old"), targetDir.listFiles()!!.map { it.name })
        }
    }
}
