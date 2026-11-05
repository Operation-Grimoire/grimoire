package io.grimoire.app.data.tts

import io.grimoire.app.data.local.entity.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsChapterContentTest {
    private val content = TtsChapterContent(TtsNowPlaying("source", 1, "Book", "/chapter", "Chapter"), emptyList())

    @Test fun followingReaderFindsTheLoadedChapter() {
        val chapters = listOf(ChapterEntity(novelId = 1, url = "/previous", name = "Previous"),
            ChapterEntity(novelId = 1, url = "/chapter", name = "Chapter"))
        assertEquals(1, content.readerChapterIndex("source", chapters))
    }

    @Test fun identicalUrlsCannotShareContentAcrossSourcesOrNovels() {
        val chapter = ChapterEntity(novelId = 1, url = "/chapter", name = "Chapter")
        assertEquals(-1, content.readerChapterIndex("other-source", listOf(chapter)))
        assertEquals(-1, content.readerChapterIndex("source", listOf(chapter.copy(novelId = 2))))
        assertEquals(-1, content.readerChapterIndex("source", listOf(chapter.copy(url = "/other"))))
    }
}
