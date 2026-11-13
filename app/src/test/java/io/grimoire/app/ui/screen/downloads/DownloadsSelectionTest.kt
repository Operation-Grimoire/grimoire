package io.grimoire.app.ui.screen.downloads

import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSelectionTest {
    @Test
    fun mixedSelectionRetainsLocalSelectionButExcludesItFromDestructiveActions() {
        val groups = listOf(group(10, 100), group(20, LOCAL_SOURCE_ID), group(30, 300))
        val target = resolveTargets(groups, setOf(10, 20))
        assertEquals(setOf(10L, 20L), target.novelIds)
        assertEquals(listOf(10L, 20L), target.chapters.map { it.id })
        assertEquals(listOf(10L), target.editableChapters.map { it.id })
    }

    @Test
    fun localOnlySelectionHasNoDestructiveTarget() {
        val target = resolveTargets(listOf(group(20, LOCAL_SOURCE_ID)), setOf(20))
        assertEquals(1, target.chapters.size)
        assertEquals(emptyList<ChapterEntity>(), target.editableChapters)
    }

    private fun group(id: Long, sourceId: Long) = NovelDownloads(
        NovelEntity(id = id, sourceId = sourceId, url = "novel-$id", title = "Novel $id"),
        listOf(ChapterEntity(id = id, novelId = id, url = "chapter-$id", name = "Chapter $id")),
    )
}
