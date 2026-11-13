package io.grimoire.app.data.book

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity

internal fun mergeBookMetadata(
    existing: NovelEntity?,
    parsed: ParsedBook,
    sourceId: Long,
    url: String,
    thumbnailUrl: String?,
    favorite: Boolean,
    markRead: Boolean,
    now: Long,
): NovelEntity {
    val current = existing ?: NovelEntity(sourceId = sourceId, url = url, title = parsed.title, favorite = favorite)
    return current.copy(
        title = parsed.title,
        thumbnailUrl = thumbnailUrl ?: current.thumbnailUrl,
        author = parsed.author,
        description = parsed.description,
        genres = parsed.genres.joinToString(","),
        // Keep the stored language when the book's language tag is absent or unknown.
        language = parsed.language
            ?.let { Language.fromCode(it.substringBefore('-')) }
            ?.takeIf { it != Language.UNKNOWN && it != Language.MULTI }
            ?.displayName ?: current.language,
        status = NovelStatus.COMPLETED.ordinal,
        lastUpdated = now,
        lastReadAt = if (markRead) now else current.lastReadAt,
    )
}

internal fun ChapterEntity.withBookReadingState(previous: ChapterEntity?): ChapterEntity = copy(
    read = previous?.read ?: false,
    readProgress = previous?.readProgress ?: 0f,
    readAnchorItemIndex = previous?.readAnchorItemIndex ?: 0,
    readAnchorItemOffset = previous?.readAnchorItemOffset ?: 0,
    firstReadAt = previous?.firstReadAt,
)
