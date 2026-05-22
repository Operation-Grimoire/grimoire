package io.grimoire.app.data.tts

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.NovelPage
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.entity.CHAPTER_PAGE_SEPARATOR
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.extension.ExtensionManager
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
        chapterDao.getChaptersOnce(novelId)

    suspend fun loadPages(pkg: String, chapter: ChapterEntity): List<NovelPage> {
        val cached = chapter.downloadedContent
        if (cached != null) {
            return cached.split(CHAPTER_PAGE_SEPARATOR)
                .mapIndexed { index, text -> NovelPage(index, text) }
                .filter { it.text.isNotBlank() }
        }
        val source = extensionManager.extensions.value
            .firstOrNull { it.info.packageName == pkg }
            ?.source
            ?: throw IllegalStateException("Source unavailable — download the chapter to listen offline")
        return source.getPageList(chapter.toChapter()).filter { it.text.isNotBlank() }
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
