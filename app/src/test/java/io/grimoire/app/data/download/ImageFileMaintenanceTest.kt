package io.grimoire.app.data.download

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageFileMaintenanceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun failedRefetchRetainsPreviousImageAndDefersPruning() {
        val directory = folder.newFolder("chapter")
        File(directory, "0").writeText("old zero")
        File(directory, "1").writeText("old one")
        File(directory, "9").writeText("old extra")
        updateImageFiles(directory, setOf(0, 1)) { index, target ->
            writeImageAtomically(target) { output ->
                output.write("new $index".toByteArray())
                if (index == 1) throw IOException("refetch interrupted")
            }
        }
        assertEquals("new 0", File(directory, "0").readText())
        assertEquals("old one", File(directory, "1").readText())
        assertEquals("old extra", File(directory, "9").readText())
        assertEquals(setOf("0", "1", "9"), directory.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun successfulPassPrunesOnlyObsoleteIndices() {
        val directory = folder.newFolder("chapter")
        File(directory, "9").writeText("old extra")
        updateImageFiles(directory, setOf(0, 1)) { index, target ->
            writeImageAtomically(target) { it.write("new $index".toByteArray()) }
        }
        assertEquals("new 0", File(directory, "0").readText())
        assertEquals("new 1", File(directory, "1").readText())
        assertFalse(File(directory, "9").exists())
    }

    @Test
    fun newStoreCleansOrphanPartsAcrossChapters() {
        val chapter = folder.newFolder("novel", "chapter")
        val orphan = File(chapter, "image-orphan.part").apply { writeText("partial") }
        val completed = File(chapter, "7").apply { writeText("complete") }
        val maintenance = ImageFileMaintenance(folder.root)
        maintenance.prepare()
        assertFalse(orphan.exists())
        assertEquals("complete", completed.readText())
        assertEquals(ImageStoreUsage(8, 1), maintenance.usage())
    }

    @Test
    fun usageExcludesActivePartsWithoutDeletingThem() {
        val chapter = folder.newFolder("chapter")
        val maintenance = ImageFileMaintenance(folder.root)
        maintenance.prepare()
        File(chapter, "7").writeText("complete")
        val active = File(chapter, "image-active.part").apply { writeText("partial") }
        assertEquals(ImageStoreUsage(8, 1), maintenance.usage())
        assertTrue(active.exists())
        ImageFileMaintenance(folder.root).prepare()
        assertFalse(active.exists())
        assertEquals("complete", File(chapter, "7").readText())
    }

    @Test
    fun cancellationPreservesOldFilesAndStopsFurtherFetches() {
        val directory = folder.newFolder("chapter")
        File(directory, "0").writeText("old zero")
        File(directory, "9").writeText("old extra")
        var calls = 0
        try {
            updateImageFiles(directory, setOf(0, 1)) { _, target ->
                calls++
                writeImageAtomically(target) {
                    it.write("partial".toByteArray())
                    throw CancellationException("stopped")
                }
            }
            fail("expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
        assertEquals("old zero", File(directory, "0").readText())
        assertEquals("old extra", File(directory, "9").readText())
        assertEquals(setOf("0", "9"), directory.listFiles()!!.map { it.name }.toSet())
    }
}
