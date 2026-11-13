package io.grimoire.app.data.book

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.epub.EpubParser
import io.grimoire.app.data.txt.TxtParser
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.encodeChapterContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sentinel URL written into encoded chapter content for an embedded image.
 * The reader replaces it with a `file://` URI from [ChapterImageStore] before
 * rendering, so the placeholder is never loaded over the network. The value is
 * retained for compatibility with existing EPUB imports.
 */
private const val LOCAL_EMBEDDED_IMAGE_URL = "local://epub-image"

/** Outcome of a book import: the stored novel's id and title. */
data class BookImportResult(val novelId: Long, val title: String)

/**
 * A parsed-but-not-yet-persisted local book awaiting user confirmation. The
 * full [ParsedBook] (including all chapter text) is held in memory so the
 * preview can be shown and the book committed without re-reading the file.
 */
data class StagedBook(
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val coverBytes: ByteArray?,
    val chapterCount: Int,
    internal val parsed: ParsedBook,
)

@Singleton
class BookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val chapterImageStore: ChapterImageStore,
) {
    /**
     * Reads and parses the book at [uri] without touching the library, so the
     * caller can show a metadata preview before the user decides to add it.
     * The temporary copy made for parsing is removed before returning; the
     * returned [StagedBook] retains everything needed by [commit].
     */
    suspend fun stage(uri: Uri): Result<StagedBook> = withContext(Dispatchers.IO) {
        runCatching {
            val originalName = originalName(uri)
            val format = detectBookFormat(context.contentResolver.getType(uri), originalName)
            val temp = File.createTempFile("import", ".${format.extension}", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open the selected file")

                val parser = when (format) {
                    BookFormat.EPUB -> EpubParser
                    BookFormat.TXT -> TxtParser
                }
                val parsed = parser.parse(temp, originalName)
                StagedBook(
                    url = "${format.extension}:${UUID.randomUUID()}",
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
     * Persists a [staged] book as a favorited local book and marks it as just
     * read, so it surfaces at the top of the library's "Last read" sort.
     */
    suspend fun commit(staged: StagedBook): Result<BookImportResult> =
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
    suspend fun importEpubBytes(
        bytes: ByteArray,
        sourceId: Long,
        url: String,
    ): Result<BookImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File.createTempFile("source", ".epub", context.cacheDir)
            try {
                temp.writeBytes(bytes)
                persist(
                    parsed = EpubParser.parse(temp, originalName = null),
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

    private fun originalName(uri: Uri): String? {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        }.getOrNull()
        return queried ?: uri.lastPathSegment
    }

    /**
     * Stores [parsed] as a fully-downloaded novel attributed to [sourceId] /
     * [url]. Re-importing the same (sourceId, url) replaces its chapters and
     * preserves existing user metadata and reading state. [favorite] applies
     * to new rows; [markRead] updates the novel's last-read time.
     */
    private suspend fun persist(
        parsed: ParsedBook,
        sourceId: Long,
        url: String,
        favorite: Boolean,
        markRead: Boolean,
    ): BookImportResult {
        val thumbnailUrl = parsed.cover?.let { cover ->
            val dir = File(context.filesDir, "epub_covers").apply { mkdirs() }
            val out = File(dir, "${UUID.randomUUID()}.${cover.extension}")
            out.writeBytes(cover.bytes)
            Uri.fromFile(out).toString()
        }

        val existing = novelDao.getBySourceUrl(sourceId, url)
        novelDao.upsert(
            mergeBookMetadata(existing, parsed, sourceId, url, thumbnailUrl, favorite, markRead,
                System.currentTimeMillis()),
        )
        // @Upsert returns -1 on the update path, so resolve the real row
        // id explicitly — otherwise chapters get novelId = -1 and the
        // insert fails with SQLITE_CONSTRAINT_FOREIGNKEY.
        val novelId = existing?.id
            ?: novelDao.getBySourceUrl(sourceId, url)?.id
            ?: error("Imported novel row not found after upsert")

        // Preserve per-chapter reading state across a re-import. Matching is
        // title-first with a positional fallback (see matchPreviousBookChapters):
        // URLs are positional ("$url/$index"), so keying on them would hand a
        // chapter the wrong state whenever a new edition shifts the offsets.
        val previous = chapterDao.getChaptersOnce(novelId)
        val priorByIndex = matchPreviousBookChapters(previous, parsed.chapters.map { it.title })

        val contents = ArrayList<String>(parsed.chapters.size)
        val entities = parsed.chapters.mapIndexed { index, ch ->
            val chapterUrl = "$url/$index"
            val prior = priorByIndex[index]
            val novelPages = ch.pages.mapIndexed { pageIndex, page ->
                when (page) {
                    is BookPage.Text -> NovelPage(pageIndex, PageContent.Text(page.text))
                    is BookPage.Image -> NovelPage(pageIndex, PageContent.Image(LOCAL_EMBEDDED_IMAGE_URL))
                }
            }
            val encoded = encodeChapterContent(novelPages)
            contents += encoded
            ChapterEntity(
                novelId = novelId,
                url = chapterUrl,
                name = ch.title,
                chapterNumber = (index + 1).toFloat(),
                downloadStatus = ChapterDownloadStatus.DOWNLOADED.ordinal,
                wordCount = encoded.countWords(),
            ).withBookReadingState(prior)
        }
        chapterDao.replaceChaptersWithContent(novelId, entities, contents)

        // Write each chapter's embedded illustration bytes to the same on-disk
        // layout DownloadManager uses for web-source images, so the reader's
        // ChapterImageStore lookup resolves them transparently.
        parsed.chapters.forEachIndexed { index, ch ->
            val images = ch.pages.mapIndexedNotNull { pageIndex, page ->
                (page as? BookPage.Image)?.let { pageIndex to it.bytes }
            }
            if (images.isNotEmpty()) {
                chapterImageStore.saveLocalImages(novelId, "$url/$index", images)
            }
        }

        return BookImportResult(novelId, parsed.title)
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
