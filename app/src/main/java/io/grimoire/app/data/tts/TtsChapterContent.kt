package io.grimoire.app.data.tts

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.app.data.local.entity.ChapterEntity

/** The active chapter's original pages, shared with a reader following playback. */
data class TtsChapterContent(val nowPlaying: TtsNowPlaying, val pages: List<NovelPage>) {
    internal fun readerChapterIndex(pkg: String, chapters: List<ChapterEntity>): Int {
        if (pkg != nowPlaying.pkg) return -1
        return chapters.indexOfFirst { it.novelId == nowPlaying.novelId && it.url == nowPlaying.chapterUrl }
    }
}
