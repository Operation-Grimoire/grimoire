package io.grimoire.app.data.cover

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists user-supplied cover images to internal storage under
 * filesDir/covers/{novelId}. Mirrors [io.grimoire.app.data.download.ChapterImageStore]'s
 * per-novel directory layout so [io.grimoire.app.data.local.SourceIdMigrator]-style
 * cleanups stay predictable. One cover per novel; saving replaces the previous file.
 */
@Singleton
class CustomCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val rootDir: File
        get() = File(context.filesDir, "covers")

    private fun novelDir(novelId: Long): File = File(rootDir, novelId.toString())

    /**
     * Copies the picked image [uri] into the novel's cover directory and returns the
     * absolute file path to store in `customCoverPath`. Replaces any existing cover.
     */
    suspend fun saveFromUri(novelId: Long, uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = novelDir(novelId)
        dir.deleteRecursively()
        dir.mkdirs()
        val dest = File(dir, "cover_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open cover image" }
            dest.outputStream().use { input.copyTo(it) }
        }
        dest.absolutePath
    }

    /** Stages a target-owned copy without deleting the target's current cover. */
    suspend fun copyForMigration(novelId: Long, sourcePath: String): File = withContext(Dispatchers.IO) {
        copyMigrationCover(File(sourcePath), novelDir(novelId))
    }

    suspend fun cleanupAfterMigration(
        novelId: Long,
        previousPath: String?,
        stagedPath: String?,
        persistedPath: String?,
    ) = withContext(Dispatchers.IO) {
        cleanupMigrationCovers(novelDir(novelId), previousPath, stagedPath, persistedPath)
    }

    /** Removes the saved cover for a novel, if any. */
    suspend fun delete(novelId: Long) {
        withContext(Dispatchers.IO) { novelDir(novelId).deleteRecursively() }
    }
}

internal fun copyMigrationCover(source: File, targetDir: File): File {
    targetDir.mkdirs()
    val copy = File.createTempFile("migrated_", ".cover", targetDir)
    try {
        source.copyTo(copy, overwrite = true)
        return copy
    } catch (failure: Throwable) {
        copy.delete()
        throw failure
    }
}

internal fun cleanupMigrationCovers(
    targetDir: File,
    previousPath: String?,
    stagedPath: String?,
    persistedPath: String?,
) {
    val ownedDir = targetDir.canonicalFile
    val active = persistedPath?.let { File(it).canonicalFile }
    for (path in listOfNotNull(previousPath, stagedPath).distinct()) {
        val file = File(path).canonicalFile
        if (file != active && file.parentFile == ownedDir) file.delete()
    }
}
