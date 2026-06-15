package io.grimoire.app.data.storage

import android.content.Context
import android.os.StatFs
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.local.TransientNovelPruner
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A point-in-time snapshot of where the app's storage is going. */
data class StorageBreakdown(
    val libraryNovels: Int,
    val libraryChapters: Int,
    val browseNovels: Int,
    val downloadedTextCount: Int,
    val downloadedTextBytes: Long,
    val downloadedImageCount: Int,
    val downloadedImageBytes: Long,
    val coverCacheBytes: Long,
    val databaseBytes: Long,
    val installerCount: Int,
    val installerBytes: Long,
    val deviceFreeBytes: Long,
    val deviceTotalBytes: Long,
) {
    /** Total on-disk bytes Grimoire accounts for here (the sum of the byte buckets). */
    val appTotalBytes: Long
        get() = downloadedTextBytes + downloadedImageBytes + coverCacheBytes +
            databaseBytes + installerBytes
}

/**
 * Measures and clears the app's on-device storage for the Data management screen.
 * Read paths are cheap aggregate queries / directory walks; the clear paths are the
 * safe, reversible ones (cover cache re-downloads, browse rows re-fetch on open).
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val chapterImageStore: ChapterImageStore,
    private val transientNovelPruner: TransientNovelPruner,
) {

    suspend fun measure(): StorageBreakdown = withContext(Dispatchers.IO) {
        val chapterStats = chapterDao.getStorageChapterStats()
        val imageUsage = chapterImageStore.usage()
        val installers = installerApks()
        val device = StatFs(context.dataDir.path)
        StorageBreakdown(
            libraryNovels = novelDao.countFavorites(),
            libraryChapters = chapterStats.libraryChapters,
            browseNovels = novelDao.countClearableBrowse(),
            downloadedTextCount = chapterStats.downloadedTextCount,
            downloadedTextBytes = chapterStats.downloadedTextBytes,
            downloadedImageCount = imageUsage.fileCount,
            downloadedImageBytes = imageUsage.bytes,
            coverCacheBytes = context.imageLoader.diskCache?.size ?: 0L,
            databaseBytes = databaseBytes(),
            installerCount = installers.size,
            installerBytes = installers.sumOf { it.length() },
            deviceFreeBytes = device.availableBytes,
            deviceTotalBytes = device.totalBytes,
        )
    }

    /** Drop the Coil cover cache (disk + memory). Covers re-download on next view. */
    suspend fun clearCoverCache() = withContext(Dispatchers.IO) {
        context.imageLoader.apply {
            diskCache?.clear()
            memoryCache?.clear()
        }
    }

    /** Force-remove eligible browse rows now; returns how many novels were dropped. */
    suspend fun clearBrowseData(): Int = transientNovelPruner.clearAll()

    /** Delete leftover installer APKs; returns how many files were removed. */
    suspend fun clearInstallerFiles(): Int = withContext(Dispatchers.IO) {
        installerApks().count { it.delete() }
    }

    /**
     * Leftover `*.apk` files at the root of [Context.getCacheDir]: the app self-update
     * download (`grimoire-update.apk`) and per-extension installers (`<package>.apk`).
     * Both are handed to the system installer and never cleaned up afterwards.
     */
    private fun installerApks(): List<File> =
        context.cacheDir
            .listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
            ?.toList()
            ?: emptyList()

    /** grimoire.db plus its write-ahead log and shared-memory sidecar files. */
    private fun databaseBytes(): Long {
        val db = context.getDatabasePath("grimoire.db")
        return listOf(db, File("${db.path}-wal"), File("${db.path}-shm"))
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
