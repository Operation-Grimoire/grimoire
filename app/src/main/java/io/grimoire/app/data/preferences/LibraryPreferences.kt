package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryDisplayMode { GRID, LIST }

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
}
