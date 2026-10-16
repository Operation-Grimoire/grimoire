package io.grimoire.app.data.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.source.web.PageListSource
import io.grimoire.app.R
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.decodeChapterContent
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.AppLocale
import io.grimoire.app.util.text
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads chapter lists and chapter text for the TTS service without going through
 * [io.grimoire.app.ui.screen.reader.ReaderViewModel]. Mirrors the reader's
 * cached-or-live loading: downloaded content is reconstructed from the database,
 * otherwise the extension source is queried.
 */
@Singleton
class TtsChapterLoader @Inject constructor(
    @ApplicationContext context: Context,
    private val extensionManager: ExtensionManager,
    private val chapterDao: ChapterDao,
) {

    private val localizedContext = AppLocale.wrap(context)

    suspend fun loadChapterList(novelId: Long): List<ChapterEntity> =
        chapterDao.getChapterMetadataOnce(novelId)

    suspend fun loadPages(pkg: String, chapter: ChapterEntity): List<NovelPage> {
        // The passed entity comes from a metadata-only list (no downloadedContent),
        // so re-read the row to serve a downloaded chapter from disk instead of the
        // network.
        val full = chapterDao.getByUrl(chapter.novelId, chapter.url) ?: chapter
        val cached = full.downloadedContent
        if (cached != null) {
            // Image pages carry no prose; drop them so read-aloud only speaks text.
            return decodeChapterContent(cached).filter { it.text.isNotBlank() }
        }
        val source = extensionManager.extensions.value
            .firstOrNull { it.info.packageName == pkg }
            ?.source
            ?: throw IllegalStateException(
                localizedContext.getString(R.string.tts_error_source_unavailable),
            )
        val pageSource = source as? PageListSource
            ?: throw IllegalStateException(
                localizedContext.getString(R.string.tts_error_source_no_text),
            )
        return pageSource.getPageList(full.toChapter()).filter { it.text.isNotBlank() }
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
