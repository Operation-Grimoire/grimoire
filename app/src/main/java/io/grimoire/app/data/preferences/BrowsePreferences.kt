package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class BrowseDisplayMode { GRID, LIST }

@Singleton
class BrowsePreferences @Inject constructor(store: PreferenceStore) {
    val displayMode = store.getEnum("browse_display_mode", BrowseDisplayMode.GRID)
    val gridColumns = store.getInt("browse_grid_columns", 2)
}
