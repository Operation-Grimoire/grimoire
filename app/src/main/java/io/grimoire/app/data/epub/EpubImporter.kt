package io.grimoire.app.data.epub

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.encodeChapterContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sentinel URL written into encoded chapter content for an EPUB-embedded image.
 * The reader replaces it with a `file://` URI from [ChapterImageStore] before
 * rendering, so the placeholder is never loaded over the network.
 */
private const val EPUB_EMBEDDED_IMAGE_URL = "local://epub-image"

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
    private val chapterImageStore: ChapterImageStore,
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
                // dc:language is a BCP-47 tag; store the English name to match how
                // source novels persist theirs (see Novel.toEntity). Unmappable or
                // absent tags keep whatever an earlier import recorded.
                language = parsed.language
                    ?.let { Language.fromCode(it.substringBefore('-')) }
                    ?.takeIf { it != Language.UNKNOWN && it != Language.MULTI }
                    ?.displayName
                    ?: existing?.language,
                status = NovelStatus.COMPLETED.ordinal,
                favorite = existing?.favorite ?: favorite,
                lastUpdated = System.currentTimeMillis(),
                chapterSortOrder = existing?.chapterSortOrder ?: 0,
                categoryId = existing?.categoryId,
                lastReadAt = if (markRead) System.currentTimeMillis() else (existing?.lastReadAt ?: 0L),
                notifyOnNewChapters = existing?.notifyOnNewChapters ?: false,
                notifyOnNewLockedChapters = existing?.notifyOnNewLockedChapters ?: false,
            ),
        )
        // @Upsert returns -1 on the update path, so resolve the real row
        // id explicitly — otherwise chapters get novelId = -1 and the
        // insert fails with SQLITE_CONSTRAINT_FOREIGNKEY.
        val novelId = existing?.id
            ?: novelDao.getBySourceUrl(sourceId, url)?.id
            ?: error("Imported novel row not found after upsert")

        // Preserve per-chapter reading state across a re-import. Matching is
        // title-first with a positional fallback (see matchPreviousEpubChapters):
        // URLs are positional ("$url/$index"), so keying on them would hand a
        // chapter the wrong state whenever a new edition shifts the offsets.
        val previous = chapterDao.getChaptersOnce(novelId)
        val priorByIndex = matchPreviousEpubChapters(previous, parsed.chapters.map { it.title })

        val entities = parsed.chapters.mapIndexed { index, ch ->
            val chapterUrl = "$url/$index"
            val prior = priorByIndex[index]
            val novelPages = ch.pages.mapIndexed { pageIndex, page ->
                when (page) {
                    is EpubPage.Text -> NovelPage(pageIndex, PageContent.Text(page.text))
                    is EpubPage.Image -> NovelPage(pageIndex, PageContent.Image(EPUB_EMBEDDED_IMAGE_URL))
                }
            }
            val encoded = encodeChapterContent(novelPages)
            ChapterEntity(
                novelId = novelId,
                url = chapterUrl,
                name = ch.title,
                chapterNumber = (index + 1).toFloat(),
                read = prior?.read ?: false,
                readProgress = prior?.readProgress ?: 0f,
                firstReadAt = prior?.firstReadAt,
                downloadStatus = ChapterDownloadStatus.DOWNLOADED.ordinal,
                downloadedContent = encoded,
                wordCount = encoded.countWords(),
            )
        }
        chapterDao.replaceChapters(novelId, entities)

        // Write each chapter's in-EPUB illustration bytes to the same on-disk
        // layout DownloadManager uses for web-source images, so the reader's
        // ChapterImageStore lookup resolves them transparently.
        parsed.chapters.forEachIndexed { index, ch ->
            val images = ch.pages.mapIndexedNotNull { pageIndex, page ->
                (page as? EpubPage.Image)?.let { pageIndex to it.bytes }
            }
            if (images.isNotEmpty()) {
                chapterImageStore.saveLocalImages(novelId, "$url/$index", images)
            }
        }

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
