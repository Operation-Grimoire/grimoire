package io.grimoire.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class ReaderModelCloseTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedSaveDuringCloseLeavesModelUsableAndCloseCanBeRetried() = runBlocking {
        val directory = temporary.newFolder().toPath()
        val model = ReaderModel(LocalLibrary(directory), "1.5.0", AtomicBoolean(true))
        val blocked = directory.resolve("library.json")
        Files.createDirectory(blocked)
        try {
            try { model.close(); fail("Close should report the save failure") }
            catch (_: IOException) { }
            Files.delete(blocked)
            model.showLibrary()
            withTimeout(5_000) { model.state.first { !it.busy } }
            assertNull(model.state.value.error)
            assertTrue("The recovered model did not execute the operation", Files.isRegularFile(blocked))
        } finally {
            if (Files.isDirectory(blocked)) Files.delete(blocked)
            model.close()
        }
    }
}
