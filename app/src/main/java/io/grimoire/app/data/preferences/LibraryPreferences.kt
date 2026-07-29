package io.grimoire.app.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryDisplayMode { GRID, LIST }

/** Sentinel [LibraryPreferences.selectedCategoryId] value for the "All" tab, which has no category. */
const val ALL_TAB_CATEGORY_ID = -1L

enum class SortField { TITLE, LAST_UPDATED, UNREAD, TOTAL, LAST_READ, USER_RATING }

enum class SortDirection { ASC, DESC }

/**
 * Tri-state library filter on novel type:
 * [ALL] shows everything, [EPUB] keeps only imported-EPUB (local) novels, [WEB]
 * keeps only extension/source-backed (web) novels. A novel is EPUB when its
 * `sourceId` is the reserved local id; everything else is a web novel.
 */
enum class NovelTypeFilter { ALL, EPUB, WEB }

/**
 * Translates a legacy `library_sort_order` enum name (e.g. "LAST_READ_DESC") into the
 * new (field, direction) split. Returns null for unrecognized values so reads fall back
 * to the new defaults instead of silently picking the wrong sort.
 */
private fun parseLegacySort(value: String): Pair<SortField, SortDirection>? = when (value) {
    "TITLE_ASC" -> SortField.TITLE to SortDirection.ASC
    "TITLE_DESC" -> SortField.TITLE to SortDirection.DESC
    "LAST_UPDATED_DESC" -> SortField.LAST_UPDATED to SortDirection.DESC
    "LAST_UPDATED_ASC" -> SortField.LAST_UPDATED to SortDirection.ASC
    "UNREAD_DESC" -> SortField.UNREAD to SortDirection.DESC
    "TOTAL_DESC" -> SortField.TOTAL to SortDirection.DESC
    "LAST_READ_DESC" -> SortField.LAST_READ to SortDirection.DESC
    else -> null
}

// Multi-select filters store the chosen values as a CSV with a leading "!" marker.
// The marker exists so we can tell "user has explicitly cleared the filter" (stored
// as "!" — meaning "show all") apart from "key was never written" (stored as the
// default "", which means "fall back to the legacy single-value preference").
private const val FILTER_SET_MARKER = "!"

@Singleton
class LibraryPreferences @Inject constructor(store: PreferenceStore) {
    val displayMode = store.getEnum("library_display_mode", LibraryDisplayMode.GRID)
    val gridColumns = store.getInt("library_grid_columns", 3)
    val showAllTab = store.getBoolean("library_show_all_tab", true)

    // Sort is split into field + direction so the UI can offer a "flip direction"
    // toggle that doesn't change which column the list is sorted by. The legacy
    // `library_sort_order` key stored a combined enum like "LAST_READ_DESC"; we
    // keep reading it as a fallback so existing preferences migrate without reset.
    private val sortFieldRaw = store.getString("library_sort_field", "")
    private val sortDirectionRaw = store.getString("library_sort_direction", "")
    private val legacySortOrder = store.getString("library_sort_order", "")

    val sortField: Preference<SortField> = MigratedSortPreference(
        key = "library_sort_field",
        default = SortField.LAST_READ,
        raw = sortFieldRaw,
        legacy = legacySortOrder,
        parseRaw = { runCatching { enumValueOf<SortField>(it) }.getOrNull() },
        legacyExtract = { parseLegacySort(it)?.first },
    )

    val sortDirection: Preference<SortDirection> = MigratedSortPreference(
        key = "library_sort_direction",
        default = SortDirection.DESC,
        raw = sortDirectionRaw,
        legacy = legacySortOrder,
        parseRaw = { runCatching { enumValueOf<SortDirection>(it) }.getOrNull() },
        legacyExtract = { parseLegacySort(it)?.second },
    )

    val filterUnreadOnly = store.getBoolean("library_filter_unread_only", false)
    val filterDownloadedOnly = store.getBoolean("library_filter_downloaded_only", false)
    val filterNotifyEnabled = store.getBoolean("library_filter_notify_enabled", false)
    val filterAutoDownloadEnabled = store.getBoolean("library_filter_auto_download_enabled", false)
    val filterType = store.getEnum("library_filter_type", NovelTypeFilter.ALL)

    // User-rating range filter (1–10, inclusive). The full 1..10 range means "no
    // restriction". Once narrowed, only novels whose rating falls inside the range
    // show — unrated novels are hidden.
    val filterMinUserRating = store.getInt("library_filter_min_user_rating", 1)
    val filterMaxUserRating = store.getInt("library_filter_max_user_rating", 10)

    // Status and source filters are multi-select sets. An empty set means "no
    // restriction" (show all), so users can either tap "All" to clear or tap any
    // combination of chips. The legacy single-value keys are read as a fallback so
    // a user who picked "Ongoing" before the upgrade still sees that filter
    // applied as the one-element set {1}.
    private val filterStatusesRaw = store.getString("library_filter_statuses", "")
    private val legacyFilterStatus = store.getInt("library_filter_status", -1)
    val filterStatuses: Preference<Set<Int>> = MigratedSetPreference(
        key = "library_filter_statuses",
        raw = filterStatusesRaw,
        legacy = legacyFilterStatus.changes(),
        legacyToSet = { if (it == -1) emptySet() else setOf(it) },
        parseElement = { it.toIntOrNull() },
        serializeElement = { it.toString() },
    )

    // Statuses excluded outright (tri-state EXCLUDE). Kept separate from the
    // include set so both remain independently empty-means-off.
    val filterStatusesExclude: Preference<Set<Int>> = store.getObject(
        key = "library_filter_statuses_exclude",
        defaultValue = emptySet(),
        serialize = { it.serializeFilterSet { v -> v.toString() } },
        deserialize = { it.deserializeFilterSet { token -> token.toIntOrNull() } },
    )

    val filterSourceIds: Preference<Set<Long>> = store.getObject(
        key = "library_filter_source_ids",
        defaultValue = emptySet(),
        serialize = { it.serializeFilterSet { v -> v.toString() } },
        deserialize = { it.deserializeFilterSet { token -> token.toLongOrNull() } },
    )

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
    val showRatingBadge = store.getBoolean("library_show_rating_badge", true)

    // Flags imported-EPUB (local) novels with a badge so they're distinguishable
    // from extension-backed novels at a glance. Only renders on local novels.
    val showEpubBadge = store.getBoolean("library_show_epub_badge", true)

    // Persists the library category the user last viewed. Stores a category id, or
    // ALL_TAB_CATEGORY_ID for the "All" tab, so the restore survives reordering and
    // resolves to a safe tab when the remembered category is hidden on reopen.
    val selectedCategoryId = store.getLong("library_selected_category_id", ALL_TAB_CATEGORY_ID)
}

/**
 * Reads a sort component (field or direction) from its own DataStore key, falling
 * back to the legacy combined `library_sort_order` value when the new key hasn't
 * been written yet. Reading the legacy value at every emission keeps the migration
 * race-free — there is no "migration in progress" window where the user sees the
 * default sort before their saved preference loads.
 */
private class MigratedSortPreference<T : Enum<T>>(
    private val key: String,
    private val default: T,
    private val raw: Preference<String>,
    private val legacy: Preference<String>,
    private val parseRaw: (String) -> T?,
    private val legacyExtract: (String) -> T?,
) : Preference<T> {
    override fun key(): String = key
    override fun defaultValue(): T = default
    override fun changes(): Flow<T> = combine(raw.changes(), legacy.changes()) { new, legacyValue ->
        new.takeIf { it.isNotEmpty() }?.let(parseRaw)
            ?: legacyValue.takeIf { it.isNotEmpty() }?.let(legacyExtract)
            ?: default
    }
    override suspend fun set(value: T) { raw.set(value.name) }
}

/**
 * Stores a Set<T> using the [FILTER_SET_MARKER] serialization. The marker lets us
 * tell "user explicitly cleared the filter" (stored as "!" → empty set) apart from
 * "key was never written" (default "" → fall back to [legacy]).
 */
private class MigratedSetPreference<T : Any, L>(
    private val key: String,
    private val raw: Preference<String>,
    private val legacy: Flow<L>,
    private val legacyToSet: (L) -> Set<T>,
    private val parseElement: (String) -> T?,
    private val serializeElement: (T) -> String,
) : Preference<Set<T>> {
    override fun key(): String = key
    override fun defaultValue(): Set<T> = emptySet()
    override fun changes(): Flow<Set<T>> = combine(raw.changes(), legacy) { r, l ->
        r.deserializeFilterSet(parseElement) ?: legacyToSet(l)
    }
    override suspend fun set(value: Set<T>) {
        raw.set(value.serializeFilterSet(serializeElement))
    }
}

private fun <T> Set<T>.serializeFilterSet(serialize: (T) -> String): String =
    FILTER_SET_MARKER + joinToString(",", transform = serialize)

private fun <T> String.deserializeFilterSet(parse: (String) -> T?): Set<T>? =
    if (startsWith(FILTER_SET_MARKER)) {
        removePrefix(FILTER_SET_MARKER)
            .split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull(parse)
            .toSet()
    } else null
