package io.grimoire.desktop

import io.grimoire.api.model.novel.Chapter
import io.grimoire.ui.reader.ReaderColorTheme
import io.grimoire.ui.reader.ReaderFont
import io.grimoire.ui.reader.ReaderTextAlign
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopReaderSettingsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun settingsPersistAcrossReopenAndClampOutOfRangeValues() {
        val directory = temporary.newFolder().toPath()
        val chosen = DesktopReaderSettings(fontSize = 20, lineHeightTimes10 = 18, paragraphSpacing = 8, font = ReaderFont.SERIF,
            colorTheme = ReaderColorTheme.SEPIA, textAlign = ReaderTextAlign.JUSTIFY, showBookPercent = false, grimoireEasterEgg = false)
        LocalLibrary(directory).use { store ->
            assertEquals(DesktopReaderSettings(), store.readerSettings())
            store.setReaderSettings(chosen)
        }
        LocalLibrary(directory).use { store ->
            assertEquals(chosen, store.readerSettings())
            store.setReaderSettings(chosen.copy(fontSize = 99, paragraphSpacing = -4))
            assertEquals(32, store.readerSettings().fontSize)
            assertEquals(0, store.readerSettings().paragraphSpacing)
        }
    }

    @Test fun unknownOrMalformedValuesFallBackOneByOne() {
        val decoded = decodeReaderSettings("""{"fontSize":22,"font":"COMIC","colorTheme":"NEON","textAlign":"CENTER"}""")
        assertEquals(DesktopReaderSettings(fontSize = 22, textAlign = ReaderTextAlign.CENTER), decoded)
        assertEquals(DesktopReaderSettings(), decodeReaderSettings("not json"))
        assertEquals(DesktopReaderSettings(), decodeReaderSettings(null))
        assertEquals(DesktopReaderSettings(colorTheme = ReaderColorTheme.BLACK),
            decodeReaderSettings(DesktopReaderSettings(colorTheme = ReaderColorTheme.BLACK).encode()))
    }

    @Test fun adjacentChapterSkipsLockedChaptersAndStopsAtTheEnds() {
        val chapters = listOf(Chapter("/1", "One"), Chapter("/2", "Two", locked = true), Chapter("/3", "Three"))
        assertEquals("/3", adjacentChapter(chapters, chapters[0], 1)?.url)
        assertEquals("/1", adjacentChapter(chapters, chapters[2], -1)?.url)
        assertNull(adjacentChapter(chapters, chapters[0], -1))
        assertNull(adjacentChapter(chapters, chapters[2], 1))
        assertNull(adjacentChapter(chapters, Chapter("/missing", "Missing"), 1))
    }

    @Test fun chapterReadFractionProratesPartiallyVisibleLastRow() {
        // Single long paragraph taller than viewport: starts at 25%, reaches 100% only at the bottom.
        assertEquals(0.25f, chapterReadFraction(1, lastVisibleIndex = 0, lastVisibleOffset = 0, lastVisibleSize = 2000, viewportEnd = 500), 1e-4f)
        assertEquals(1.0f, chapterReadFraction(1, lastVisibleIndex = 0, lastVisibleOffset = -1500, lastVisibleSize = 2000, viewportEnd = 500), 1e-4f)
        // Multi-paragraph chapter where the final long paragraph only shows its top 10%.
        assertEquals(0.91f, chapterReadFraction(10, lastVisibleIndex = 9, lastVisibleOffset = 400, lastVisibleSize = 1000, viewportEnd = 500), 1e-4f)
        assertEquals(1.0f, chapterReadFraction(10, lastVisibleIndex = 9, lastVisibleOffset = -500, lastVisibleSize = 1000, viewportEnd = 500), 1e-4f)
        // Empty chapter.
        assertEquals(0f, chapterReadFraction(0, lastVisibleIndex = 0, lastVisibleOffset = 0, lastVisibleSize = 0, viewportEnd = 500), 1e-4f)
    }
}
