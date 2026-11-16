package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.reading.storage.SavedChapterRepository
import io.grimoire.reading.storage.ChapterStorageObservation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class ChapterOrigin { SAVED, PRELOADED, SOURCE }

data class LoadedChapter(
    val pages: List<NovelPage>, val origin: ChapterOrigin, val cacheRevision: String? = null,
    val storageObservation: ChapterStorageObservation? = null,
)

enum class ChapterLoadFailure { OFFLINE, SOURCE_UNAVAILABLE, PAGES_UNSUPPORTED }

class ChapterLoadException(val reason: ChapterLoadFailure) : IllegalStateException(reason.name)

/** The platform resolves source identity, permissions and capabilities only when needed. */
sealed interface ChapterPageAccess {
    class Available(val read: suspend () -> List<NovelPage>) : ChapterPageAccess
    data class Unavailable(val reason: ChapterLoadFailure) : ChapterPageAccess
}

/** Selects original pages. Presentation transforms and user-facing errors belong to callers. */
class ChapterLoader(private val savedChapters: SavedChapterRepository) {
    /**
     * A null key means the caller has no stored novel identity. Only absence permits fallback;
     * empty saved/preloaded lists are present. Storage/source failures propagate unchanged.
     * Source access is resolved lazily, after saved and preloaded content have been considered.
     */
    suspend fun load(
        key: ChapterKey?,
        preloaded: List<NovelPage>? = null,
        sourceAccess: suspend () -> ChapterPageAccess,
    ): LoadedChapter {
        currentCoroutineContext().ensureActive()
        val saved = key?.let { savedChapters.readObserved(it) }
        currentCoroutineContext().ensureActive()
        val savedPages = saved?.pages
        if (savedPages != null) return LoadedChapter(savedPages, ChapterOrigin.SAVED, saved.cacheRevision, saved.observation)
        if (preloaded != null) return LoadedChapter(preloaded, ChapterOrigin.PRELOADED, storageObservation = saved?.observation)
        val access = sourceAccess()
        currentCoroutineContext().ensureActive()
        val pages = when (access) {
            is ChapterPageAccess.Available -> access.read()
            is ChapterPageAccess.Unavailable -> throw ChapterLoadException(access.reason)
        }
        // A source may ignore cancellation and return normally. Never publish its late result.
        currentCoroutineContext().ensureActive()
        return LoadedChapter(pages, ChapterOrigin.SOURCE, storageObservation = saved?.observation)
    }
}
