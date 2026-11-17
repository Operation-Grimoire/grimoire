package io.grimoire.app.data.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.source.web.PageListSource
import io.grimoire.app.R
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.ChapterLoader
import io.grimoire.reading.ChapterPageAccess
import io.grimoire.reading.ChapterLoadFailure
import io.grimoire.reading.ChapterLoadException
import io.grimoire.reading.LoadedChapter
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.util.AppLocale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads chapter lists and original pages for the TTS service without going through
 * [io.grimoire.app.ui.screen.reader.ReaderViewModel]. Uses the shared reader's
 * saved-first loading: downloaded content is reconstructed from the database,
 * otherwise the extension source is queried. Active reader snapshots are handled
 * separately by TtsPlaybackManager; this loader preserves its immediate source lookup.
 */
@Singleton
class TtsChapterLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionManager: ExtensionManager,
    private val chapterDao: ChapterDao,
    private val novelDao: NovelDao,
    private val chapterLoader: ChapterLoader,
) {

    // Resolved per use: these singletons outlive an in-app language change.
    private val localizedContext get() = AppLocale.wrap(context)

    suspend fun loadChapterList(novelId: Long): List<ChapterEntity> =
        chapterDao.getChapterMetadataOnce(novelId)

    suspend fun loadChapter(pkg: String, chapter: ChapterEntity): LoadedChapter {
        // Re-read the row (the passed entity may be a stale snapshot) and serve a
        // downloaded chapter's saved pages instead of hitting the network.
        val full = chapterDao.getByUrl(chapter.novelId, chapter.url) ?: chapter
        val key = novelDao.getById(full.novelId)?.let { novel ->
            ChapterKey(NovelKey(novel.sourceId, novel.url), full.url)
        }
        return try {
            chapterLoader.load(key) {
                val source = extensionManager.extensions.value
                    .firstOrNull { it.info.packageName == pkg }?.source
                val pageSource = source as? PageListSource
                when {
                    source == null -> ChapterPageAccess.Unavailable(ChapterLoadFailure.SOURCE_UNAVAILABLE)
                    pageSource == null -> ChapterPageAccess.Unavailable(ChapterLoadFailure.PAGES_UNSUPPORTED)
                    else -> ChapterPageAccess.Available { pageSource.getPageList(full.toChapter()) }
                }
            }
        } catch (failure: ChapterLoadException) {
            throw IllegalStateException(localizedContext.getString(when (failure.reason) {
                ChapterLoadFailure.SOURCE_UNAVAILABLE -> R.string.tts_error_source_unavailable
                ChapterLoadFailure.PAGES_UNSUPPORTED -> R.string.tts_error_source_no_text
                ChapterLoadFailure.OFFLINE -> error("TTS does not restrict source access to offline mode")
            }))
        }
    }
}

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
)
