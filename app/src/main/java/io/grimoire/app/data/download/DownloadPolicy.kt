package io.grimoire.app.data.download

import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.ChapterEntity

internal fun canManageDownloads(sourceId: Long?): Boolean =
    sourceId != null && sourceId != LOCAL_SOURCE_ID

internal fun chaptersForDownloadChanges(
    chapters: List<ChapterEntity>,
    sourceIds: Map<Long, Long?>,
): List<ChapterEntity> = chapters.filter { canManageDownloads(sourceIds[it.novelId]) }
