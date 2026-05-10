package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryDisplayMode { GRID, LIST }

@Singleton
class LibraryPreferences @Inject constructor(store: PreferenceStore) {
    val displayMode = store.getEnum("library_display_mode", LibraryDisplayMode.GRID)
    val gridColumns = store.getInt("library_grid_columns", 3)
    val showAllTab = store.getBoolean("library_show_all_tab", true)
}
