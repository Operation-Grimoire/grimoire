package io.grimoire.app.data.tts

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.reading.LoadedChapter
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.storage.ChapterStorageObservation

/** The pages sent to the engine and the authority captured when their original body loaded. */
data class TtsChapterInput(
    val loaded: LoadedChapter,
    val pages: List<NovelPage>,
    val document: CanonicalDocument?,
) {
    val observation: ChapterStorageObservation? get() = loaded.storageObservation

    companion object {
        fun fromLoaded(loaded: LoadedChapter): TtsChapterInput {
            val pages = loaded.pages.toList()
            val observation = loaded.storageObservation
            return TtsChapterInput(loaded.copy(pages = pages), pages, observation?.let { CanonicalDocument.fromPages(it.key, pages) })
        }
    }
}

/** The active chapter's original pages, shared with a reader following playback. */
data class TtsChapterContent(
    val nowPlaying: TtsNowPlaying,
    val loaded: LoadedChapter,
) {
    internal fun readerChapterIndex(pkg: String, chapters: List<ChapterEntity>): Int {
        if (pkg != nowPlaying.pkg) return -1
        return chapters.indexOfFirst { it.novelId == nowPlaying.novelId && it.url == nowPlaying.chapterUrl }
    }
}
