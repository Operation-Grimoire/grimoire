package io.grimoire.app.data.download

import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPolicyTest {
    @Test
    fun mixedBatchKeepsOnlyRefetchableNovelsAndPreservesChapterOrder() {
        val chapters = listOf(chapter(1, 10), chapter(2, 20), chapter(3, 10), chapter(4, 30))
        val result = chaptersForDownloadChanges(
            chapters,
            mapOf(10L to 123L, 20L to LOCAL_SOURCE_ID, 30L to null),
        )
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun importedAndMissingNovelsCannotBeDeletedOrForceRefreshed() {
        val chapters = listOf(chapter(1, 10), chapter(2, 20))
        assertEquals(emptyList<ChapterEntity>(), chaptersForDownloadChanges(chapters, mapOf(10L to LOCAL_SOURCE_ID)))
    }

    private fun chapter(id: Long, novelId: Long) =
        ChapterEntity(id = id, novelId = novelId, url = "chapter-$id", name = "Chapter $id")
}
