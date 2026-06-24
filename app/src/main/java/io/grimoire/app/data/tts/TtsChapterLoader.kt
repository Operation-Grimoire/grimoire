package io.grimoire.app.data.tts

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.source.web.PageListSource
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.decodeChapterContent
import io.grimoire.app.extension.ExtensionManager
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
    private val extensionManager: ExtensionManager,
    private val chapterDao: ChapterDao,
) {

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
            ?: throw IllegalStateException("Source unavailable — download the chapter to listen offline")
        val pageSource = source as? PageListSource
            ?: throw IllegalStateException("This source can't read chapter text — download to listen offline")
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
