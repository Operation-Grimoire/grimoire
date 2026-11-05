package io.grimoire.app.data.libraryupdate

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.app.data.local.entity.NovelEntity

internal fun shouldSyncNovel(novel: NovelEntity): Boolean =
    novel.effectiveStatus != NovelStatus.COMPLETED.ordinal

/** Keeps local edits and good metadata when a source returns incomplete fields. */
internal fun mergeNovelMetadata(old: NovelEntity, fetched: Novel, now: Long): NovelEntity {
    val merged = old.copy(
        title = fetched.title.takeIf { it.isNotBlank() } ?: old.title,
        thumbnailUrl = fetched.thumbnailUrl?.takeIf { it.isNotBlank() } ?: old.thumbnailUrl,
        author = fetched.author?.takeIf { it.isNotBlank() } ?: old.author,
        description = fetched.description?.takeIf { it.isNotBlank() } ?: old.description,
        genres = fetched.genres.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }?.joinToString(",") ?: old.genres,
        status = if (fetched.status != NovelStatus.UNKNOWN) fetched.status.ordinal else old.status,
        rating = fetched.rating ?: old.rating,
        ratingCount = fetched.ratingCount ?: old.ratingCount,
        language = fetched.language.takeIf { it != Language.UNKNOWN && it != Language.MULTI }
            ?.displayName ?: old.language,
    )
    // Seed cache freshness on the first successful fetch; later no-op syncs do not write.
    return if (merged == old && old.lastUpdated > 0L) old else merged.copy(lastUpdated = now)
}
