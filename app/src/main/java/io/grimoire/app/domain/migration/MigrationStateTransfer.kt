package io.grimoire.app.domain.migration

import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import java.util.Locale

internal fun NovelEntity.withMigratedMetadata(
    source: NovelEntity,
    migratedCoverPath: String?,
): NovelEntity = copy(
    favorite = true,
    categoryId = source.categoryId,
    chapterSortOrder = source.chapterSortOrder,
    lastReadAt = source.lastReadAt,
    notifyOnNewChapters = source.notifyOnNewChapters,
    notifyOnNewLockedChapters = source.notifyOnNewLockedChapters,
    autoDownloadNewChapters = source.autoDownloadNewChapters,
    userRating = source.userRating,
    readerTextAlign = source.readerTextAlign,
    customCoverPath = migratedCoverPath,
    customCoverUrl = source.customCoverUrl,
    overrideTitle = source.overrideTitle,
    overrideAuthor = source.overrideAuthor,
    overrideDescription = source.overrideDescription,
    overrideStatus = source.overrideStatus,
    overrideGenres = source.overrideGenres,
)

internal fun matchMigratedChapters(
    source: List<ChapterEntity>,
    target: List<ChapterEntity>,
): List<ChapterEntity> {
    // Count unread chapters too: their repeated numbers are equally ambiguous.
    val byNumber = source.indices.groupBy { source[it].effectiveNumber() }
        .filter { (number, indices) -> number > 0f && indices.size == 1 }
        .mapValues { it.value.single() }
    val byName = source.indices.groupBy { source[it].name.chapterNameKey() }
        .filter { (name, indices) -> name.isNotEmpty() && indices.size == 1 }
        .mapValues { it.value.single() }
    val targetNumbers = target.groupingBy { it.effectiveNumber() }.eachCount()
    val targetNames = target.groupingBy { it.name.chapterNameKey() }.eachCount()
    val matches = arrayOfNulls<ChapterEntity>(target.size)
    val claimed = HashSet<Int>()

    target.forEachIndexed { index, chapter ->
        val name = chapter.name.chapterNameKey()
        val previous = byName[name]?.takeIf { targetNames[name] == 1 } ?: return@forEachIndexed
        matches[index] = source[previous]
        claimed += previous
    }
    target.forEachIndexed { index, chapter ->
        if (matches[index] != null) return@forEachIndexed
        val number = chapter.effectiveNumber()
        val previous = byNumber[number]?.takeIf { targetNumbers[number] == 1 } ?: return@forEachIndexed
        val sourceVolume = source[previous].volumeNumber()
        val targetVolume = chapter.volumeNumber()
        if (sourceVolume != null && targetVolume != null && sourceVolume != targetVolume) return@forEachIndexed
        if (claimed.add(previous)) matches[index] = source[previous]
    }

    return target.mapIndexedNotNull { index, chapter ->
        val match = matches[index] ?: return@mapIndexedNotNull null
        if (!match.read && match.readProgress <= 0f) return@mapIndexedNotNull null
        chapter.copy(
            read = match.read,
            readProgress = match.readProgress,
            readAnchorItemIndex = match.readAnchorItemIndex,
            readAnchorItemOffset = match.readAnchorItemOffset,
            firstReadAt = chapter.firstReadAt ?: match.firstReadAt,
        )
    }
}

private val CHAPTER_NUMBER = Regex(
    """\b(?:chapter|chap|ch|episode|ep|part)\.?\s*(\d+(?:\.\d+)?)""",
    RegexOption.IGNORE_CASE,
)
private val VOLUME_NUMBER = Regex("""\b(?:volume|vol|book|v)\.?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("""\s+""")

private fun ChapterEntity.effectiveNumber(): Float {
    if (chapterNumber > 0f) return chapterNumber
    CHAPTER_NUMBER.find(name)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
    return -1f
}

private fun ChapterEntity.volumeNumber(): Int? =
    VOLUME_NUMBER.find(name)?.groupValues?.get(1)?.toIntOrNull()

private fun String.chapterNameKey(): String =
    trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")
