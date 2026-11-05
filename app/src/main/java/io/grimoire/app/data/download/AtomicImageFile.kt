package io.grimoire.app.data.download

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The reader filename appears only after a complete image has been written. */
internal fun writeImageAtomically(
    destination: File,
    expectedLength: Long = -1L,
    write: (OutputStream) -> Unit,
) {
    val temporary = File.createTempFile("image-", ".part", destination.parentFile)
    try {
        temporary.outputStream().use { write(it) }
        val length = temporary.length()
        if (length == 0L || (expectedLength >= 0L && length != expectedLength)) {
            throw IOException("Incomplete chapter image")
        }
        Files.move(
            temporary.toPath(), destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        temporary.delete()
    }
}
