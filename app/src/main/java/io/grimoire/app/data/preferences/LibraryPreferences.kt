package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryDisplayMode { GRID, LIST }

/** Sentinel [LibraryPreferences.selectedCategoryId] value for the "All" tab, which has no category. */
const val ALL_TAB_CATEGORY_ID = -1L

enum class LibrarySort {
    TITLE_ASC, TITLE_DESC, LAST_UPDATED_DESC, LAST_UPDATED_ASC, UNREAD_DESC, TOTAL_DESC, LAST_READ_DESC
}

@Singleton
class LibraryPreferences @Inject constructor(store: PreferenceStore) {
    val displayMode = store.getEnum("library_display_mode", LibraryDisplayMode.GRID)
    val gridColumns = store.getInt("library_grid_columns", 3)
    val showAllTab = store.getBoolean("library_show_all_tab", true)
    val sortOrder = store.getEnum("library_sort_order", LibrarySort.LAST_READ_DESC)
    val filterStatus = store.getInt("library_filter_status", -1)
    val filterUnreadOnly = store.getBoolean("library_filter_unread_only", false)
    val filterDownloadedOnly = store.getBoolean("library_filter_downloaded_only", false)
    val includeHiddenInAll = store.getBoolean("library_include_hidden_in_all", false)

    // When false, locked chapters are excluded from the "total" used to compute the
    // read count and percentage shown in library badges and on the novel details
    // page, so progress reflects only chapters the user can actually read.
    val includeLockedInTotals = store.getBoolean("library_include_locked_in_totals", false)

    // Cover-badge visibility toggles. Each badge still only renders when its
    // underlying count is greater than zero.
    val showReadBadge = store.getBoolean("library_show_read_badge", true)
    val showDownloadedBadge = store.getBoolean("library_show_downloaded_badge", true)
    val showLockedBadge = store.getBoolean("library_show_locked_badge", true)

    // Persists the library category the user last viewed. Stores a category id, or
    // ALL_TAB_CATEGORY_ID for the "All" tab, so the restore survives reordering and
    // resolves to a safe tab when the remembered category is hidden on reopen.
    val selectedCategoryId = store.getLong("library_selected_category_id", ALL_TAB_CATEGORY_ID)
}
