package io.grimoire.app.ui.screen.library

import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.effectiveTotal
import io.grimoire.app.data.preferences.ALL_TAB_CATEGORY_ID
import io.grimoire.app.data.preferences.SortDirection
import io.grimoire.app.data.preferences.SortField

internal data class LibraryFilterInputs(
    val novels: List<NovelEntity>?,
    val categories: List<CategoryEntity>,
    val chapterStats: Map<Long, NovelChapterStats>,
    val showAllTab: Boolean,
    val sortField: SortField,
    val sortDirection: SortDirection,
    val filterStatuses: Set<Int>,
    val filterUnreadOnly: Boolean,
    val filterDownloadedOnly: Boolean,
    val filterSourceIds: Set<Long>,
    val isUnlocked: Boolean,
    val hiddenCategoryIds: Set<Long>,
    val includeHiddenInAll: Boolean,
    val includeLockedInTotals: Boolean,
    val searchQuery: String,
)

internal data class LibraryTab(
    val categoryId: Long,
    val label: String,
    val novels: List<NovelEntity>?,
)

/**
 * Pure projection of all library tabs from a single snapshot of inputs.
 *
 * Returns one [LibraryTab] per visible tab (optionally including the synthetic "All" tab)
 * with novels already filtered, sorted, and search-matched.
 *
 * Returns `null` for tab.novels until the underlying novels list has emitted at least
 * once, so callers can distinguish "still loading" from "no matches".
 */
internal fun buildLibraryTabs(inputs: LibraryFilterInputs): List<LibraryTab> {
    val allCategoryName = "All"
    val tabSpecs = buildList {
        if (inputs.showAllTab) {
            add(LibraryTab(categoryId = ALL_TAB_CATEGORY_ID, label = allCategoryName, novels = null))
        }
        inputs.categories.forEach { cat ->
            add(LibraryTab(categoryId = cat.id, label = cat.name, novels = null))
        }
    }
    if (tabSpecs.isEmpty()) return emptyList()
    return tabSpecs.mapIndexed { index, spec ->
        spec.copy(
            novels = computeTabNovels(
                tabIndex = index,
                inputs = inputs,
            ),
        )
    }
}

internal fun computeTabNovels(
    tabIndex: Int,
    inputs: LibraryFilterInputs,
): List<NovelEntity>? = with(inputs) {
    val loaded = novels ?: return null
    val allTabOffset = if (showAllTab) 1 else 0
    val isAllTab = showAllTab && tabIndex == 0
    val excludeHidden = !isUnlocked || (isAllTab && !includeHiddenInAll)
    val baseFiltered = if (excludeHidden) {
        loaded.filter { it.categoryId !in hiddenCategoryIds }
    } else loaded
    val tabFiltered = when {
        isAllTab -> baseFiltered
        else -> {
            val catIndex = tabIndex - allTabOffset
            val cat = categories.getOrNull(catIndex)
            when {
                cat == null -> baseFiltered
                cat.isDefault -> baseFiltered.filter { it.categoryId == null }
                else -> baseFiltered.filter { it.categoryId == cat.id }
            }
        }
    }
    // Build an ASC comparator for the field, then flip when the user wants DESC.
    // Centralizing the flip keeps the per-field branch from having to spell out
    // both directions and guarantees every field supports both ASC and DESC.
    val ascComparator: Comparator<NovelEntity> = when (sortField) {
        SortField.TITLE -> Comparator { a, b ->
            String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
        }
        SortField.LAST_UPDATED -> compareBy { it.lastUpdated }
        SortField.UNREAD -> compareBy {
            chapterStats[it.id]?.let { s -> s.effectiveTotal(includeLockedInTotals) - s.readCount } ?: 0
        }
        SortField.TOTAL -> compareBy {
            chapterStats[it.id]?.effectiveTotal(includeLockedInTotals) ?: 0
        }
        SortField.LAST_READ -> compareBy { it.lastReadAt }
    }
    val comparator = if (sortDirection == SortDirection.DESC) ascComparator.reversed() else ascComparator
    val trimmedQuery = searchQuery.trim()
    return tabFiltered
        .filter { novel ->
            (filterStatuses.isEmpty() || novel.status in filterStatuses) &&
            (!filterUnreadOnly || (chapterStats[novel.id]?.let { it.effectiveTotal(includeLockedInTotals) - it.readCount > 0 } == true)) &&
            (!filterDownloadedOnly || (chapterStats[novel.id]?.downloadedCount ?: 0) > 0) &&
            (filterSourceIds.isEmpty() || novel.sourceId in filterSourceIds) &&
            (trimmedQuery.isEmpty() ||
                novel.title.contains(trimmedQuery, ignoreCase = true) ||
                (novel.author?.contains(trimmedQuery, ignoreCase = true) == true))
        }
        .sortedWith(comparator)
}
