package io.grimoire.app.data.download

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AtomicImageFileTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun completeImageAppearsOnlyAfterWritingFinishes() {
        val target = File(folder.root, "7")
        writeImageAtomically(target, expectedLength = 8) {
            it.write("complete".toByteArray())
            assertFalse(target.exists())
        }
        assertEquals("complete", target.readText())
        assertEquals(listOf("7"), folder.root.listFiles()!!.map { it.name })
    }

    @Test
    fun interruptedImageNeverBecomesAReaderFile() {
        val target = File(folder.root, "7")
        val failure = IOException("stream interrupted")
        try {
            writeImageAtomically(target) {
                it.write("partial".toByteArray())
                throw failure
            }
            fail("expected interrupted write")
        } catch (e: IOException) {
            assertSame(failure, e)
        }
        assertFalse(target.exists())
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test
    fun failedReplacementPreservesPreviousCompleteImage() {
        val target = File(folder.root, "7").apply { writeText("old image") }
        try {
            writeImageAtomically(target) {
                it.write("partial".toByteArray())
                throw IOException("stream interrupted")
            }
            fail("expected interrupted write")
        } catch (_: IOException) {
            assertEquals("old image", target.readText())
        }
        assertEquals(listOf("7"), folder.root.listFiles()!!.map { it.name })
    }

    @Test
    fun declaredLengthMismatchDoesNotPublishImage() {
        val target = File(folder.root, "7")
        try {
            writeImageAtomically(target, expectedLength = 50) { it.write("short".toByteArray()) }
            fail("expected length mismatch")
        } catch (_: IOException) {
            assertFalse(target.exists())
        }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test
    fun emptyResponseDoesNotPublishImage() {
        val target = File(folder.root, "7")
        try {
            writeImageAtomically(target) { }
            fail("expected empty image rejection")
        } catch (_: IOException) {
            assertFalse(target.exists())
        }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test
    fun completeReplacementPublishesNewImage() {
        val target = File(folder.root, "7").apply { writeText("old image") }
        writeImageAtomically(target, expectedLength = 9) { it.write("new image".toByteArray()) }
        assertEquals("new image", target.readText())
        assertEquals(listOf("7"), folder.root.listFiles()!!.map { it.name })
    }
}
