package io.grimoire.app.data.download

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.NovelPage
import io.grimoire.api.network.defaultOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
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

    private fun chapterDir(novelId: Long, chapterUrl: String): File =
        File(File(rootDir, novelId.toString()), chapterUrl.sha256())

    /**
     * Fetches every illustration in [pages] and writes it alongside the chapter.
     * Best-effort: a failed image is skipped so the reader can still stream it.
     */
    suspend fun saveImages(novelId: Long, chapterUrl: String, pages: List<NovelPage>) {
        val images = pages.filter { it.imageUrl != null }
        if (images.isEmpty()) return
        withContext(Dispatchers.IO) {
            val dir = chapterDir(novelId, chapterUrl)
            // Drop any stale files first so a re-download can't leave orphans behind.
            dir.deleteRecursively()
            dir.mkdirs()
            for (page in images) {
                val url = page.imageUrl ?: continue
                runCatching {
                    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        val body = response.body
                        if (response.isSuccessful && body != null) {
                            File(dir, page.index.toString()).outputStream().use { output ->
                                body.byteStream().copyTo(output)
                            }
                        }
                    }
                }
            }
        }
    }

    /** A `file://` URI for a saved illustration, or null when it was not downloaded. */
    fun localImageUri(novelId: Long, chapterUrl: String, index: Int): String? {
        val file = File(chapterDir(novelId, chapterUrl), index.toString())
        return if (file.isFile) Uri.fromFile(file).toString() else null
    }

    /** Removes the saved illustrations for a single chapter. */
    suspend fun deleteChapter(novelId: Long, chapterUrl: String) {
        withContext(Dispatchers.IO) { chapterDir(novelId, chapterUrl).deleteRecursively() }
    }

    /** Removes the saved illustrations for every chapter of a novel. */
    suspend fun deleteNovel(novelId: Long) {
        withContext(Dispatchers.IO) { File(rootDir, novelId.toString()).deleteRecursively() }
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
