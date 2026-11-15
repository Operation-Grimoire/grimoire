package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.ChapterPage
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.web.CursorPaginatedSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderModelSourceTest {
    @Test
    fun cursorSourceLoadsEveryChapterThroughTheSharedWalker() = runBlocking {
        val source = object : CursorPaginatedSource {
            override val name = "Cursor fixture"
            override val lang = Language.EN
            val calls = mutableListOf<String?>()
            override suspend fun getNovelDetails(novel: Novel) = novel
            override suspend fun getChapterList(novel: Novel, cursor: String?): ChapterPage {
                calls += cursor
                val page = calls.size
                return ChapterPage(
                    listOf(Chapter("chapter-$page", "Chapter $page", chapterNumber = page.toFloat())),
                    nextCursor = if (page < 3) "cursor-$page" else null,
                )
            }
        }
        val chapters = sourceChapters(source, Novel("book", "Book", Language.EN))
        assertEquals(listOf(null, "cursor-1", "cursor-2"), source.calls)
        assertEquals(listOf(1f, 2f, 3f), chapters.map { it.chapterNumber })
    }
}
