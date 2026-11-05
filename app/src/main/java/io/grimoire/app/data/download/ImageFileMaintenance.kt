package io.grimoire.app.data.download

import java.io.File
import kotlinx.coroutines.CancellationException

/** Retain previous images on failed refetches; prune old indices after a complete pass. */
internal fun updateImageFiles(
    directory: File,
    indices: Set<Int>,
    save: (Int, File) -> Unit,
) {
    directory.mkdirs()
    var complete = true
    for (index in indices) {
        try {
            save(index, File(directory, index.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            complete = false
        }
    }
    if (complete) {
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.name.toIntOrNull()?.let { it !in indices } == true) file.delete()
        }
    }
}

/** One instance per store/process; all writers prepare before creating temporary files. */
internal class ImageFileMaintenance(private val root: File) {
    private var prepared = false

    @Synchronized
    fun prepare() {
        if (prepared) return
        root.walkTopDown().forEach { file ->
            if (file.isFile && file.isTemporaryImage()) file.delete()
        }
        prepared = true
    }

    fun usage(): ImageStoreUsage {
        prepare()
        var bytes = 0L
        var count = 0
        root.walkTopDown().forEach { file ->
            if (file.isFile && !file.isTemporaryImage()) {
                bytes += file.length()
                count++
            }
        }
        return ImageStoreUsage(bytes, count)
    }
}

private fun File.isTemporaryImage(): Boolean = name.startsWith("image-") && name.endsWith(".part")
