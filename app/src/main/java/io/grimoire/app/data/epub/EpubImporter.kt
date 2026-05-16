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

@Singleton
class EpubImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
) {
    /**
     * Imports the EPUB at [uri], fully extracting every chapter into the
     * database so the source file is never needed again. The user's original
     * file is only read (never modified or deleted); the temporary copy we make
     * for parsing is removed before returning.
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

                val parsed = EpubParser.parse(temp)
                val novelUrl = "epub:${UUID.randomUUID()}"

                val thumbnailUrl = parsed.cover?.let { cover ->
                    val dir = File(context.filesDir, "epub_covers").apply { mkdirs() }
                    val out = File(dir, "${UUID.randomUUID()}.${cover.extension}")
                    out.writeBytes(cover.bytes)
                    Uri.fromFile(out).toString()
                }

                val novelId = novelDao.upsert(
                    NovelEntity(
                        sourceId = LOCAL_SOURCE_ID,
                        url = novelUrl,
                        title = parsed.title,
                        thumbnailUrl = thumbnailUrl,
                        author = parsed.author,
                        description = parsed.description,
                        genres = parsed.genres.joinToString(","),
                        status = NovelStatus.COMPLETED.ordinal,
                        favorite = true,
                        lastUpdated = System.currentTimeMillis(),
                    ),
                )

                chapterDao.insertAll(
                    parsed.chapters.mapIndexed { index, ch ->
                        ChapterEntity(
                            novelId = novelId,
                            url = "$novelUrl/$index",
                            name = ch.title,
                            chapterNumber = (index + 1).toFloat(),
                            downloadStatus = ChapterDownloadStatus.DOWNLOADED.ordinal,
                            downloadedContent = ch.content,
                            wordCount = ch.content.countWords(),
                        )
                    },
                )

                parsed.title
            } finally {
                temp.delete()
            }
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
