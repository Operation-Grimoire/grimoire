package io.grimoire.app.data.epub

import io.grimoire.app.data.local.entity.ChapterEntity

/**
 * Matches a re-imported EPUB's chapters ([titles], in spine order) to the
 * [previous] rows stored for the novel, so read state survives a re-import.
 * Returns a list aligned with [titles]: the prior row each chapter inherits
 * state from, or null when it starts unread.
 *
 * Chapter URLs are positional ("$novelUrl/$index"), so a purely index-keyed
 * carry-over misattributes read state whenever a new edition shifts chapter
 * offsets — an added prologue makes every following chapter inherit its
 * predecessor's state. Titles are the stable identity: match by title first
 * (only when the title is unique on both sides), and fall back to position
 * only when the chapter count is unchanged, i.e. there is no evidence of a
 * shift. A chapter matched by neither rule inherits nothing rather than
 * guessing.
 */
internal fun matchPreviousEpubChapters(
    previous: List<ChapterEntity>,
    titles: List<String>,
): List<ChapterEntity?> {
    if (previous.isEmpty()) return List(titles.size) { null }
    // chapterNumber is index + 1 from the original import, so this restores
    // spine order regardless of how the DAO returned the rows.
    val ordered = previous.sortedBy { it.chapterNumber }
    val result = arrayOfNulls<ChapterEntity>(titles.size)
    val claimed = BooleanArray(ordered.size)

    val prevIndexByTitle = ordered.indices.asSequence()
        .filter { ordered[it].name.isNotBlank() }
        .groupBy { ordered[it].name.trim() }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    val titleCounts = titles.groupingBy { it.trim() }.eachCount()

    titles.forEachIndexed { i, raw ->
        val title = raw.trim()
        if (title.isEmpty() || titleCounts[title] != 1) return@forEachIndexed
        val at = prevIndexByTitle[title] ?: return@forEachIndexed
        result[i] = ordered[at]
        claimed[at] = true
    }

    if (ordered.size == titles.size) {
        for (i in titles.indices) {
            if (result[i] == null && !claimed[i]) {
                result[i] = ordered[i]
                claimed[i] = true
            }
        }
    }
    return result.toList()
}
