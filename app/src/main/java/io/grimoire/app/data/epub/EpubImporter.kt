package io.grimoire.app.data.epub

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of an EPUB import: the stored novel's id and title. */
data class EpubImportResult(val novelId: Long, val title: String)

@Singleton
class EpubImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
) {
    /**
     * Imports the EPUB at [uri] as a local book (no backing extension),
     * auto-added to the library. The user's original file is only read; the
     * temporary copy we make for parsing is removed before returning.
     *
     * @return the imported novel's title on success.
     */
    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File.createTempFile("import", ".epub", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open the selected file")

                importFile(
                    file = temp,
                    sourceId = LOCAL_SOURCE_ID,
                    url = "epub:${UUID.randomUUID()}",
                    favorite = true,
                ).getOrThrow().title
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Stores [bytes] (a complete EPUB delivered by an extension source) as a
     * fully-downloaded novel attributed to [sourceId] / [url]. The temporary
     * file used for parsing is removed before returning.
     */
    suspend fun importBytes(
        bytes: ByteArray,
        sourceId: Long,
        url: String,
    ): Result<EpubImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File.createTempFile("source", ".epub", context.cacheDir)
            try {
                temp.writeBytes(bytes)
                importFile(temp, sourceId, url).getOrThrow()
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Parses [file] and stores it as a fully-downloaded novel attributed to
     * [sourceId] / [url]. Used both for local imports and for EPUBs delivered by
     * an extension source. Re-importing the same (sourceId, url) replaces its
     * chapters and preserves the existing library state (favorite, category,
     * sort, last-read).
     *
     * The caller owns [file]'s lifecycle.
     */
    suspend fun importFile(
        file: File,
        sourceId: Long,
        url: String,
        favorite: Boolean = false,
    ): Result<EpubImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = EpubParser.parse(file)

            val thumbnailUrl = parsed.cover?.let { cover ->
                val dir = File(context.filesDir, "epub_covers").apply { mkdirs() }
                val out = File(dir, "${UUID.randomUUID()}.${cover.extension}")
                out.writeBytes(cover.bytes)
                Uri.fromFile(out).toString()
            }

            val existing = novelDao.getBySourceUrl(sourceId, url)
            novelDao.upsert(
                NovelEntity(
                    id = existing?.id ?: 0L,
                    sourceId = sourceId,
                    url = url,
                    title = parsed.title,
                    thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
                    author = parsed.author,
                    description = parsed.description,
                    genres = parsed.genres.joinToString(","),
                    status = NovelStatus.COMPLETED.ordinal,
                    favorite = existing?.favorite ?: favorite,
                    lastUpdated = System.currentTimeMillis(),
                    chapterSortOrder = existing?.chapterSortOrder ?: 0,
                    categoryId = existing?.categoryId,
                    lastReadAt = existing?.lastReadAt ?: 0L,
                ),
            )
            // @Upsert returns -1 on the update path, so resolve the real row
            // id explicitly — otherwise chapters get novelId = -1 and the
            // insert fails with SQLITE_CONSTRAINT_FOREIGNKEY.
            val novelId = existing?.id
                ?: novelDao.getBySourceUrl(sourceId, url)?.id
                ?: error("Imported novel row not found after upsert")

            // Preserve per-chapter reading state across a re-import: chapter
            // URLs are deterministic ("$url/$index"), so carry read progress
            // from any existing chapter with the same URL into its replacement.
            val previous = chapterDao.getChaptersOnce(novelId).associateBy { it.url }

            chapterDao.replaceChapters(
                novelId,
                parsed.chapters.mapIndexed { index, ch ->
                    val chapterUrl = "$url/$index"
                    val prior = previous[chapterUrl]
                    ChapterEntity(
                        novelId = novelId,
                        url = chapterUrl,
                        name = ch.title,
                        chapterNumber = (index + 1).toFloat(),
                        read = prior?.read ?: false,
                        readProgress = prior?.readProgress ?: 0f,
                        firstReadAt = prior?.firstReadAt,
                        downloadStatus = ChapterDownloadStatus.DOWNLOADED.ordinal,
                        downloadedContent = ch.content,
                        wordCount = ch.content.countWords(),
                    )
                },
            )

            EpubImportResult(novelId, parsed.title)
        }
    }
}

private fun String.countWords(): Int {
    var count = 0
    var inWord = false
    for (ch in this) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}
