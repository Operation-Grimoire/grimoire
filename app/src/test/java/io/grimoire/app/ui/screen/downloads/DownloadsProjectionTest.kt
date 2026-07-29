package io.grimoire.app.ui.screen.downloads

import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsProjectionTest {

    private fun novel(id: Long, title: String = "Novel $id") =
        NovelEntity(id = id, sourceId = 1L, url = "u$id", title = title)

    private fun chapter(
        id: Long,
        novelId: Long,
        status: ChapterDownloadStatus,
        number: Float = id.toFloat(),
    ) = ChapterEntity(
        id = id,
        novelId = novelId,
        url = "c$id",
        name = "Chapter $id",
        chapterNumber = number,
        downloadStatus = status.ordinal,
    )

    private fun novelMap(vararg novels: NovelEntity) = novels.associateBy { it.id }

    @Test
    fun `groups chapters by novel and drops chapters with no matching novel`() {
        val chapters = listOf(
            chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
            chapter(2, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
            chapter(3, novelId = 99, status = ChapterDownloadStatus.DOWNLOADED), // novel missing
        )

        val result = groupDownloads(chapters, novelMap(novel(1)))

        assertEquals(1, result.size)
        assertEquals(1L, result[0].novel.id)
        assertEquals(listOf(1L, 2L), result[0].chapters.map { it.id })
    }

    @Test
    fun `sorts chapters within a novel by status then chapter number`() {
        val chapters = listOf(
            chapter(1, novelId = 1, status = ChapterDownloadStatus.ERROR, number = 1f),
            chapter(2, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED, number = 2f),
            chapter(3, novelId = 1, status = ChapterDownloadStatus.QUEUED, number = 3f),
            chapter(4, novelId = 1, status = ChapterDownloadStatus.DOWNLOADING, number = 4f),
        )

        val sorted = groupDownloads(chapters, novelMap(novel(1)))[0].chapters.map { it.id }

        // downloading (4) → queued (3) → downloaded (2) → error (1)
        assertEquals(listOf(4L, 3L, 2L, 1L), sorted)
    }

    @Test
    fun `novels with active or queued work sort ahead of fully-downloaded ones`() {
        val chapters = listOf(
            chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
            chapter(2, novelId = 2, status = ChapterDownloadStatus.DOWNLOADING),
        )

        val result = groupDownloads(chapters, novelMap(novel(1), novel(2)))

        assertEquals(listOf(2L, 1L), result.map { it.novel.id })
    }

    @Test
    fun `counts tally every status from the full set`() {
        val chapters = listOf(
            chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
            chapter(2, novelId = 1, status = ChapterDownloadStatus.REDOWNLOAD_QUEUED),
            chapter(3, novelId = 1, status = ChapterDownloadStatus.REDOWNLOADING),
            chapter(4, novelId = 1, status = ChapterDownloadStatus.ERROR),
            chapter(5, novelId = 1, status = ChapterDownloadStatus.REDOWNLOAD_ERROR),
        )

        val counts = groupDownloads(chapters, novelMap(novel(1)))[0].counts

        assertEquals(1, counts.downloaded)
        assertEquals(1, counts.queued)
        assertEquals(1, counts.downloading)
        assertEquals(2, counts.error)
    }

    @Test
    fun `empty filter returns the input unchanged`() {
        val downloads = groupDownloads(
            listOf(chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED)),
            novelMap(novel(1)),
        )

        assertEquals(downloads, applyStatusFilter(downloads, emptySet()))
    }

    @Test
    fun `filter narrows chapters, drops emptied novels, and preserves full counts`() {
        val chapters = listOf(
            chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
            chapter(2, novelId = 1, status = ChapterDownloadStatus.ERROR),
            chapter(3, novelId = 2, status = ChapterDownloadStatus.DOWNLOADED), // no error → dropped
        )
        val downloads = groupDownloads(chapters, novelMap(novel(1), novel(2)))

        val filtered = applyStatusFilter(downloads, setOf(DownloadStatusFilter.FAILED))

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].novel.id)
        assertEquals(listOf(2L), filtered[0].chapters.map { it.id })
        // Header counts still reflect the full set (1 downloaded + 1 failed), not the filter.
        assertEquals(1, filtered[0].counts.downloaded)
        assertEquals(1, filtered[0].counts.error)
        assertNull(filtered.firstOrNull { it.novel.id == 2L })
    }

    @Test
    fun `multi-select filter unions the selected statuses`() {
        val chapters = listOf(
            chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
            chapter(2, novelId = 1, status = ChapterDownloadStatus.QUEUED),
            chapter(3, novelId = 1, status = ChapterDownloadStatus.ERROR),
        )
        val downloads = groupDownloads(chapters, novelMap(novel(1)))

        val filtered = applyStatusFilter(
            downloads,
            setOf(DownloadStatusFilter.DONE, DownloadStatusFilter.QUEUED),
        )

        assertTrue(filtered[0].chapters.map { it.id }.containsAll(listOf(1L, 2L)))
        assertEquals(2, filtered[0].chapters.size)
    }

    @Test
    fun `countByFilter tallies chapters per category across unfiltered groups`() {
        val grouped = groupDownloads(
            listOf(
                chapter(1, novelId = 1, status = ChapterDownloadStatus.DOWNLOADING),
                chapter(2, novelId = 1, status = ChapterDownloadStatus.QUEUED),
                chapter(3, novelId = 1, status = ChapterDownloadStatus.DOWNLOADED),
                chapter(4, novelId = 2, status = ChapterDownloadStatus.ERROR),
                chapter(5, novelId = 2, status = ChapterDownloadStatus.REDOWNLOAD_ERROR),
                chapter(6, novelId = 2, status = ChapterDownloadStatus.DOWNLOADED),
            ),
            novelMap(novel(1), novel(2)),
        )

        val counts = countByFilter(grouped)

        assertEquals(1, counts[DownloadStatusFilter.DOWNLOADING])
        assertEquals(1, counts[DownloadStatusFilter.QUEUED])
        assertEquals(2, counts[DownloadStatusFilter.DONE])
        assertEquals(2, counts[DownloadStatusFilter.FAILED])
    }

    @Test
    fun `countByFilter is empty-safe`() {
        val counts = countByFilter(emptyList())
        assertTrue(counts.values.all { it == 0 })
    }
}
