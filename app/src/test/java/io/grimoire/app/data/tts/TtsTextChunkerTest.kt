package io.grimoire.app.data.tts

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsTextChunkerTest {
    @Test fun fullReaderPagesKeepImagesAndSeparatorsOutOfSpeechAndPreservePageIndices() {
        val pages = listOf(NovelPage(0, PageContent.Text("First paragraph.")),
            NovelPage(1, PageContent.Image("https://example.invalid/image")),
            NovelPage(2, PageContent.Separator()), NovelPage(3, PageContent.Text("  ")),
            NovelPage(4, PageContent.Text("Last paragraph.")))
        assertEquals(listOf(Utterance(0, "First paragraph."), Utterance(4, "Last paragraph.")),
            TtsTextChunker.chunk(pages, Locale.ENGLISH))
    }

    @Test fun illustrationOnlyChapterStillHasNoReadableText() {
        assertTrue(TtsTextChunker.chunk(listOf(NovelPage(0, PageContent.Image("image")),
            NovelPage(1, PageContent.Separator())), Locale.ENGLISH).isEmpty())
    }
}
