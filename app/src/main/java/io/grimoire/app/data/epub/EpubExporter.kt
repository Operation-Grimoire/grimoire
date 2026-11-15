package io.grimoire.app.data.epub

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.network.defaultOkHttpClient
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.download.ChapterImageStore
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.decodeChapterContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of an EPUB export: the novel title and how many chapters were written. */
data class EpubExportResult(val title: String, val chapterCount: Int)

/**
 * Writes any library novel back out as a standards-compliant EPUB 3 file at a
 * user-chosen [Uri]. Works for both source-downloaded novels and books that were
 * themselves imported from an EPUB: in either case the chapter text lives in
 * `chapter_content` and the illustrations on disk in
 * [ChapterImageStore], so the exporter never needs the backing extension.
 *
 * Only chapters whose content is actually present locally (see
 * [ChapterDownloadStatus.HAS_CONTENT_ORDINALS]) are written — a web novel must be
 * downloaded first. The produced file round-trips through [EpubParser].
 */
@Singleton
class EpubExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val chapterImageStore: ChapterImageStore,
) {
    private val httpClient by lazy { defaultOkHttpClient() }
    private val novelDao get() = database.novelDao()
    private val chapterDao get() = database.chapterDao()

    suspend fun export(novelId: Long, dest: Uri): Result<EpubExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val novel = novelDao.getById(novelId) ?: error("Novel not found")
                // Validate before opening/truncating the destination. The storage codec
                // preserves controls, but XML 1.0 cannot represent them faithfully.
                listOf(novel.effectiveTitle, novel.effectiveAuthor.orEmpty(),
                    (novel.overrideDescription ?: novel.description).orEmpty(),
                    novel.overrideGenres ?: novel.genres, novel.language.orEmpty()).forEach(::requireEpubXmlText)
                // Fail promptly without fetching a cover for an empty export.
                // Recheck inside the transaction because downloads can change.
                require(chapterDao.getDownloadedChapterMetadata(novelId).isNotEmpty()) {
                    "No downloaded chapters to export. Download chapters first."
                }
                // Network/cover work must not hold a database transaction.
                val cover = loadCover(novel)?.let { bytes ->
                    val ext = imageExtension(bytes)
                    CoverEntry("cover.$ext", imageMediaType(ext), bytes)
                }

                // Spool one chapter at a time. All content is validated before the
                // destination is opened, without retaining the whole novel in RAM.
                val temporary = File.createTempFile("grimoire-export-", ".epub", context.cacheDir)
                val chapterCount: Int
                try {
                    chapterCount = database.withTransaction {
                        require(novelDao.getById(novelId) == novel) { "Novel changed during export. Try again." }
                        val chapters = chapterDao.getDownloadedChapterMetadata(novelId)
                        require(chapters.isNotEmpty()) {
                            "No downloaded chapters to export. Download chapters first."
                        }
                        ZipOutputStream(temporary.outputStream()).use { zip ->
                            writeEpub(zip, novelId, novel, chapters, cover)
                        }
                        chapters.size
                    }
                    val output = context.contentResolver.openOutputStream(dest)
                        ?: error("Unable to open the selected file for writing")
                    output.use { stream ->
                        temporary.inputStream().use { it.copyTo(stream) }
                    }
                } finally {
                    temporary.delete()
                }
                EpubExportResult(novel.effectiveTitle, chapterCount)
            }
        }

    private suspend fun writeEpub(
        zip: ZipOutputStream,
        novelId: Long,
        novel: NovelEntity,
        chapters: List<ChapterEntity>,
        cover: CoverEntry?,
    ) {
        // The mimetype entry must be first and stored uncompressed (OCF spec).
        zip.putStored("mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))

        zip.putText(
            "META-INF/container.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent(),
        )

        // Collect per-chapter illustrations from the on-disk image store, keyed by
        // the page index the reader resolves them by.
        data class ChapterDoc(
            val id: String,
            val href: String,
            val title: String,
            val images: List<ImageEntry>,
        )

        val chapterDocs = chapters.mapIndexed { index, chapter ->
            requireEpubXmlText(chapter.name)
            val stored = chapterDao.getContent(chapter.id)
                ?: error("Chapter content was removed during export: ${chapter.name}")
            val cacheRevision = database.chapterDownloadStateDao().find(chapter.id)?.cacheRevision
            val pages = decodeChapterContent(stored)
            val images = ArrayList<ImageEntry>()
            val body = StringBuilder()
            body.append("    <h1>").append(escape(chapter.name)).append("</h1>\n")
            for (page in pages) {
                when (val content = page.content) {
                    is PageContent.Text -> {
                        // Check before trim so invalid leading/trailing controls
                        // cannot disappear silently at the XML boundary.
                        requireEpubXmlText(content.text)
                        val text = content.text.trim()
                        if (text.isNotEmpty()) {
                            body.append("    <p>").append(escape(text)).append("</p>\n")
                        }
                    }
                    is PageContent.Image -> {
                        val file = chapterImageStore.localImageFile(novelId, chapter.url, page.index, cacheRevision)
                        if (file != null) {
                            val bytes = file.readBytes()
                            val ext = imageExtension(bytes)
                            val href = "images/ch${index}_${page.index}.$ext"
                            images += ImageEntry(
                                id = "img_${index}_${page.index}",
                                href = href,
                                mediaType = imageMediaType(ext),
                            )
                            zip.putStored("OEBPS/$href", bytes)
                            body.append("    <p><img src=\"").append(href).append("\" alt=\"\"/></p>\n")
                        }
                    }
                    is PageContent.Separator -> body.append("    <hr/>\n")
                }
            }
            val href = "ch$index.xhtml"
            zip.putText("OEBPS/$href", chapterXhtml(chapter.name, body.toString()))
            ChapterDoc(
                id = "ch$index",
                href = href,
                title = chapter.name,
                images = images,
            )
        }

        // nav.xhtml — the EPUB3 table of contents.
        val toc = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n")
            append("  <head><title>Contents</title></head>\n")
            append("  <body>\n    <nav epub:type=\"toc\" id=\"toc\">\n      <ol>\n")
            for (doc in chapterDocs) {
                append("        <li><a href=\"").append(doc.href).append("\">")
                    .append(escape(doc.title)).append("</a></li>\n")
            }
            append("      </ol>\n    </nav>\n  </body>\n</html>\n")
        }

        // content.opf — package metadata, manifest, and spine.
        val opf = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\">\n")
            append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
            append("    <dc:identifier id=\"bookid\">urn:uuid:").append(UUID.randomUUID()).append("</dc:identifier>\n")
            append("    <dc:title>").append(escape(novel.effectiveTitle)).append("</dc:title>\n")
            append("    <dc:language>").append(escape(novel.language?.takeIf { it.isNotBlank() } ?: "en")).append("</dc:language>\n")
            novel.effectiveAuthor?.takeIf { it.isNotBlank() }?.let {
                append("    <dc:creator>").append(escape(it)).append("</dc:creator>\n")
            }
            (novel.overrideDescription ?: novel.description)?.takeIf { it.isNotBlank() }?.let {
                append("    <dc:description>").append(escape(it)).append("</dc:description>\n")
            }
            (novel.overrideGenres ?: novel.genres).split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                append("    <dc:subject>").append(escape(it)).append("</dc:subject>\n")
            }
            append("    <meta property=\"dcterms:modified\">").append(utcTimestamp()).append("</meta>\n")
            cover?.let { append("    <meta name=\"cover\" content=\"cover-image\"/>\n") }
            append("  </metadata>\n")
            append("  <manifest>\n")
            append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            cover?.let {
                append("    <item id=\"cover-image\" href=\"").append(it.href)
                    .append("\" media-type=\"").append(it.mediaType).append("\" properties=\"cover-image\"/>\n")
            }
            for (doc in chapterDocs) {
                append("    <item id=\"").append(doc.id).append("\" href=\"").append(doc.href)
                    .append("\" media-type=\"application/xhtml+xml\"/>\n")
                for (img in doc.images) {
                    append("    <item id=\"").append(img.id).append("\" href=\"").append(img.href)
                        .append("\" media-type=\"").append(img.mediaType).append("\"/>\n")
                }
            }
            append("  </manifest>\n")
            append("  <spine>\n")
            for (doc in chapterDocs) {
                append("    <itemref idref=\"").append(doc.id).append("\"/>\n")
            }
            append("  </spine>\n")
            append("</package>\n")
        }

        zip.putText("OEBPS/content.opf", opf)
        zip.putText("OEBPS/nav.xhtml", toc)
        cover?.let { zip.putStored("OEBPS/${it.href}", it.bytes) }
    }

    private fun chapterXhtml(title: String, body: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n")
        append("  <head><title>").append(escape(title)).append("</title></head>\n")
        append("  <body>\n").append(body).append("  </body>\n</html>\n")
    }

    /** Best-effort cover bytes from the custom override, custom url, or source thumbnail. */
    private fun loadCover(novel: NovelEntity): ByteArray? {
        novel.customCoverPath?.let { path ->
            runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()?.let { return it }
        }
        val candidate = novel.customCoverUrl ?: novel.thumbnailUrl ?: return null
        return runCatching {
            when {
                candidate.startsWith("file://") || candidate.startsWith("/") ->
                    File(Uri.parse(candidate).path ?: candidate).takeIf { it.isFile }?.readBytes()
                candidate.startsWith("content://") ->
                    context.contentResolver.openInputStream(Uri.parse(candidate))?.use { it.readBytes() }
                candidate.startsWith("http") ->
                    httpClient.newCall(Request.Builder().url(candidate).build()).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                else -> null
            }
        }.getOrNull()
    }

    private data class CoverEntry(val href: String, val mediaType: String, val bytes: ByteArray)
    private data class ImageEntry(val id: String, val href: String, val mediaType: String)
}

private fun ZipOutputStream.putText(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun ZipOutputStream.putStored(name: String, bytes: ByteArray) {
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        crc = CRC32().apply { update(bytes) }.value
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

private fun escape(s: String): String = escapeEpubXml(s)

private fun utcTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(System.currentTimeMillis())

/** Sniff a raster image's type from its magic bytes; defaults to jpg. */
private fun imageExtension(bytes: ByteArray): String = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
    bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() -> "gif"
    bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() -> "webp"
    else -> "jpg"
}

private fun imageMediaType(ext: String): String = when (ext) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> "image/jpeg"
}
