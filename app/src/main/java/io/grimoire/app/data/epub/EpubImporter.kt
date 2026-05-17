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

/**
 * A parsed-but-not-yet-persisted local EPUB awaiting user confirmation. The
 * full [ParsedEpub] (including all chapter text) is held in memory so the
 * preview can be shown and the book committed without re-reading the file.
 */
data class StagedEpub(
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val coverBytes: ByteArray?,
    val chapterCount: Int,
    internal val parsed: ParsedEpub,
)

@Singleton
class EpubImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
) {
    /**
     * Reads and parses the EPUB at [uri] without touching the library, so the
     * caller can show a metadata preview before the user decides to add it.
     * The temporary copy made for parsing is removed before returning; the
     * returned [StagedEpub] retains everything needed by [commit].
     */
    suspend fun stage(uri: Uri): Result<StagedEpub> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File.createTempFile("import", ".epub", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open the selected file")

                val parsed = EpubParser.parse(temp)
                StagedEpub(
                    url = "epub:${UUID.randomUUID()}",
                    title = parsed.title,
                    author = parsed.author,
                    description = parsed.description,
                    genres = parsed.genres,
                    coverBytes = parsed.cover?.bytes,
                    chapterCount = parsed.chapters.size,
                    parsed = parsed,
                )
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Persists a [staged] EPUB as a favorited local book and marks it as just
     * read, so it surfaces at the top of the library's "Last read" sort.
     */
    suspend fun commit(staged: StagedEpub): Result<EpubImportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                persist(
                    parsed = staged.parsed,
                    sourceId = LOCAL_SOURCE_ID,
                    url = staged.url,
                    favorite = true,
                    markRead = true,
                )
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
                persist(
                    parsed = EpubParser.parse(temp),
                    sourceId = sourceId,
                    url = url,
                    favorite = false,
                    markRead = false,
                )
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Stores [parsed] as a fully-downloaded novel attributed to [sourceId] /
     * [url]. Re-importing the same (sourceId, url) replaces its chapters and
     * preserves the existing library state (favorite, category, sort,
     * last-read) unless [favorite] / [markRead] override it.
     */
    private suspend fun persist(
        parsed: ParsedEpub,
        sourceId: Long,
        url: String,
        favorite: Boolean,
        markRead: Boolean,
    ): EpubImportResult {
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
                lastReadAt = if (markRead) System.currentTimeMillis() else (existing?.lastReadAt ?: 0L),
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

        return EpubImportResult(novelId, parsed.title)
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
