package io.grimoire.app.data.download

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.network.defaultOkHttpClient
import io.grimoire.app.util.imageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists chapter illustrations to internal storage so downloaded chapters render
 * offline instead of re-fetching them from the network.
 *
 * Files are keyed by novel id and chapter URL. Both survive the chapter-list refresh
 * that reassigns chapter row ids (ChapterDao.replaceChapters), so a re-fetched download
 * keeps pointing at images already on disk.
 */
@Singleton
class ChapterImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Reuse the extension network stack so illustrations carry the same WebView
    // cf_clearance cookie + User-Agent as the chapter text request.
    private val httpClient by lazy { defaultOkHttpClient() }

    private val rootDir: File
        get() = File(context.filesDir, "chapter_images")

    private val maintenance by lazy { ImageFileMaintenance(rootDir) }

    private fun chapterDir(novelId: Long, chapterUrl: String): File =
        File(File(rootDir, novelId.toString()), chapterUrl.sha256())

    /**
     * Fetches every illustration in [pages] and writes it alongside the chapter.
     * Best-effort: a failed image is skipped so the reader can still stream it.
     */
    suspend fun saveImages(novelId: Long, chapterUrl: String, pages: List<NovelPage>) {
        val images = pages.mapNotNull { page -> page.imageUrl?.let { page.index to it } }.toMap()
        if (images.isEmpty()) return
        withContext(Dispatchers.IO) {
            maintenance.prepare()
            updateImageFiles(chapterDir(novelId, chapterUrl), images.keys) { index, destination ->
                httpClient.newCall(Request.Builder().url(images.getValue(index)).build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) throw IOException("Image download failed")
                    writeImageAtomically(destination, body.contentLength()) { output ->
                        body.byteStream().copyTo(output)
                    }
                }
            }
        }
    }

    /**
     * Persists already-in-hand illustration bytes for a chapter (e.g. images
     * unpacked from an imported EPUB). Mirrors [saveImages]'s on-disk layout
     * so [localImageUri] resolves them the same way, but skips the HTTP fetch
     * because the bytes are already available.
     */
    suspend fun saveLocalImages(
        novelId: Long,
        chapterUrl: String,
        images: List<Pair<Int, ByteArray>>,
    ) {
        if (images.isEmpty()) return
        val indexed = images.toMap()
        withContext(Dispatchers.IO) {
            maintenance.prepare()
            updateImageFiles(chapterDir(novelId, chapterUrl), indexed.keys) { index, destination ->
                val bytes = indexed.getValue(index)
                writeImageAtomically(destination, bytes.size.toLong()) { it.write(bytes) }
            }
        }
    }

    /** A `file://` URI for a saved illustration, or null when it was not downloaded. */
    fun localImageUri(novelId: Long, chapterUrl: String, index: Int): String? =
        localImageFile(novelId, chapterUrl, index)?.let { Uri.fromFile(it).toString() }

    /** The on-disk [File] for a saved illustration, or null when it was not downloaded. */
    fun localImageFile(novelId: Long, chapterUrl: String, index: Int): File? =
        File(chapterDir(novelId, chapterUrl), index.toString()).takeIf { it.isFile }

    /** Removes the saved illustrations for a single chapter. */
    suspend fun deleteChapter(novelId: Long, chapterUrl: String) {
        withContext(Dispatchers.IO) { chapterDir(novelId, chapterUrl).deleteRecursively() }
    }

    /** Removes the saved illustrations for every chapter of a novel. */
    suspend fun deleteNovel(novelId: Long) {
        withContext(Dispatchers.IO) { File(rootDir, novelId.toString()).deleteRecursively() }
    }

    /** Total bytes and file count of every saved illustration, for the Data screen. */
    suspend fun usage(): ImageStoreUsage = withContext(Dispatchers.IO) {
        maintenance.usage()
    }

}

data class ImageStoreUsage(val bytes: Long, val fileCount: Int)

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
