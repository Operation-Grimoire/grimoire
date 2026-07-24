package io.grimoire.app.ui.screen.downloads

import androidx.annotation.StringRes
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.R

/**
 * Each chip in the downloads filter row represents a logical "category" of download
 * statuses (downloading, queued, …). Storing the selection as a `Set<DownloadStatusFilter>`
 * lets the user pick any combination — the predicate the list filters on is the union of
 * every selected category's [ordinals].
 */
internal enum class DownloadStatusFilter(@StringRes val labelRes: Int, val ordinals: Set<Int>) {
    DOWNLOADING(R.string.downloads_filter_downloading, ChapterDownloadStatus.DOWNLOADING_ORDINALS),
    QUEUED(R.string.downloads_filter_queued, ChapterDownloadStatus.QUEUED_ORDINALS),
    DONE(R.string.downloads_filter_done, setOf(ChapterDownloadStatus.DOWNLOADED.ordinal)),
    FAILED(R.string.downloads_filter_failed, ChapterDownloadStatus.ERROR_ORDINALS),
}

/** Per-novel tallies for the section header, computed from the full (unfiltered) chapter set. */
internal data class DownloadCounts(
    val downloaded: Int = 0,
    val queued: Int = 0,
    val downloading: Int = 0,
    val error: Int = 0,
)

// Within a novel: active (downloading) first, then queued, then done, then failed;
// ties broken by chapter number.
private val STATUS_ORDER = mapOf(
    ChapterDownloadStatus.DOWNLOADING.ordinal to 0,
    ChapterDownloadStatus.REDOWNLOADING.ordinal to 0,
    ChapterDownloadStatus.QUEUED.ordinal to 1,
    ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal to 1,
    ChapterDownloadStatus.DOWNLOADED.ordinal to 2,
    ChapterDownloadStatus.ERROR.ordinal to 3,
    ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal to 3,
)

/**
 * Group downloaded chapters by novel, sort within each novel, attach per-novel [DownloadCounts],
 * and order novels so those with active/queued work float to the top. Pure: the caller resolves
 * [novelById] from the DB; a chapter whose novel is missing is dropped.
 */
internal fun groupDownloads(
    chapters: List<ChapterEntity>,
    novelById: Map<Long, NovelEntity>,
): List<NovelDownloads> =
    chapters.groupBy { it.novelId }
        .mapNotNull { (novelId, chs) ->
            val novel = novelById[novelId] ?: return@mapNotNull null
            val sorted = chs.sortedWith(
                compareBy({ STATUS_ORDER[it.downloadStatus] ?: 4 }, { it.chapterNumber }),
            )
            NovelDownloads(novel, sorted, countByStatus(sorted))
        }
        .sortedByDescending { nd ->
            nd.chapters.any { it.downloadStatus in ChapterDownloadStatus.IN_FLIGHT_ORDINALS }
        }

private fun countByStatus(chapters: List<ChapterEntity>): DownloadCounts {
    var downloaded = 0
    var queued = 0
    var downloading = 0
    var error = 0
    for (c in chapters) when (c.downloadStatus) {
        ChapterDownloadStatus.DOWNLOADED.ordinal -> downloaded++
        ChapterDownloadStatus.QUEUED.ordinal,
        ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal -> queued++
        ChapterDownloadStatus.DOWNLOADING.ordinal,
        ChapterDownloadStatus.REDOWNLOADING.ordinal -> downloading++
        ChapterDownloadStatus.ERROR.ordinal,
        ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal -> error++
    }
    return DownloadCounts(downloaded, queued, downloading, error)
}

/**
 * Narrow each novel's chapter list to the union of the selected filters' statuses, dropping
 * novels left with nothing. [DownloadCounts] are preserved from the full set so the header
 * still shows the complete tally. An empty filter set returns the input unchanged.
 */
internal fun applyStatusFilter(
    downloads: List<NovelDownloads>,
    filters: Set<DownloadStatusFilter>,
): List<NovelDownloads> {
    if (filters.isEmpty()) return downloads
    val ordinals = filters.flatMapTo(mutableSetOf()) { it.ordinals }
    return downloads.mapNotNull { nd ->
        val visible = nd.chapters.filter { it.downloadStatus in ordinals }
        if (visible.isEmpty()) null else nd.copy(chapters = visible)
    }
}
