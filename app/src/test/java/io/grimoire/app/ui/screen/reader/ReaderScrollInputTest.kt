package io.grimoire.app.ui.screen.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.storage.ChapterContentPresence
import io.grimoire.reading.storage.ChapterStorageObservation
import io.grimoire.reading.storage.ReadingStateBinding
import io.grimoire.reading.storage.ReadingStateSession
import io.grimoire.reading.storage.ReadingStateWriterKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScrollInputTest {
    @Test fun consumedDragAndFlingKeepTheirWriterAcrossRebinding() = runBlocking<Unit> {
        val key = ChapterKey(NovelKey(1, "/novel"), "/chapter")
        val content = ChapterStorageObservation(key, "content", "generation", ChapterContentPresence.SAVED)
        val document = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text("Body"))))
        val old = ReadingStateSession("old", ReadingStateBinding(key, "scope", "incarnation", 0),
            content, document, ReadingStateWriterKind.VISUAL)
        val fresh = old.copy(id = "fresh", binding = old.binding.copy(intentRevision = 1))
        var active = old
        val captured = mutableListOf<ReadingStateSession>()
        val input = ReaderScrollInput({ active }, { captured.add(it) })
        val movement = Offset(0f, -12f)

        input.onPostScroll(movement, Offset.Zero, NestedScrollSource.SideEffect)
        input.onPreScroll(movement, NestedScrollSource.UserInput)
        input.onPostScroll(Offset.Zero, movement, NestedScrollSource.UserInput)
        assertEquals(emptyList<ReadingStateSession>(), captured)

        input.onPreScroll(movement, NestedScrollSource.UserInput)
        active = fresh
        input.onPostScroll(movement, Offset.Zero, NestedScrollSource.UserInput)
        input.onPreFling(Velocity.Zero)
        input.onPostScroll(movement, Offset.Zero, NestedScrollSource.SideEffect)
        input.onPostFling(Velocity.Zero, Velocity.Zero)
        input.onPostScroll(movement, Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(listOf(old, old), captured)

        input.onPreScroll(movement, NestedScrollSource.UserInput)
        input.onPostScroll(movement, Offset.Zero, NestedScrollSource.UserInput)
        input.onPreFling(Velocity.Zero)
        input.resetVisualInput()
        input.onPostScroll(movement, Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(listOf(old, old, fresh), captured)
    }
}
