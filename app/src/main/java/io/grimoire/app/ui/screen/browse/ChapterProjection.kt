package io.grimoire.app.ui.screen.browse

import io.grimoire.app.data.local.entity.ChapterEntity

/**
 * Pure projection of the chapter list for the detail screen.
 *
 * Applies the user's sort selection first, then the title search (case-insensitive)
 * if a query is present. Whitespace-only queries are treated as empty so the
 * full sorted list still surfaces while the user is typing.
 */
internal fun projectChapters(
    chapters: List<ChapterEntity>,
    sort: ChapterSort,
    searchQuery: String,
): List<ChapterEntity> {
    val sorted = when (sort) {
        ChapterSort.NUMBER_ASC -> chapters
        ChapterSort.NUMBER_DESC -> chapters.reversed()
        ChapterSort.DATE_ASC -> chapters.sortedBy { it.uploadDate }
        ChapterSort.DATE_DESC -> chapters.sortedByDescending { it.uploadDate }
    }
    val trimmed = searchQuery.trim()
    return if (trimmed.isEmpty()) sorted
    else sorted.filter { it.name.contains(trimmed, ignoreCase = true) }
}
