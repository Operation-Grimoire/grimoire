package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class BrowseDisplayMode { GRID, LIST }

@Singleton
class BrowsePreferences @Inject constructor(store: PreferenceStore) {
    val displayMode = store.getEnum("browse_display_mode", BrowseDisplayMode.GRID)
    val gridColumns = store.getInt("browse_grid_columns", 3)

    /** Show the NovelUpdates shortcuts on the Browse home. */
    val showNovelUpdates = store.getBoolean("browse_show_novelupdates", true)

    /**
     * When true, pinned sources also appear in their language group on the
     * Browse home (duplicated). Default false — pinned show only in the Pinned
     * section.
     */
    val duplicatePinnedInLanguages = store.getBoolean("browse_duplicate_pinned", false)

    /**
     * Package names the user has pinned on the Browse home. Stored as a
     * newline-joined string; blank lines are dropped on read so a trailing
     * separator can never resurrect an empty package name.
     */
    val pinnedSources: Preference<Set<String>> = store.getObject(
        key = "browse_pinned_sources",
        defaultValue = emptySet(),
        serialize = { it.joinToString("\n") },
        deserialize = { raw -> raw.split("\n").filter { it.isNotBlank() }.toSet() },
    )
}
