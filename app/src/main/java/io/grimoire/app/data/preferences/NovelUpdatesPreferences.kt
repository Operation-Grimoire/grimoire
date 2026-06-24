package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelUpdatesPreferences @Inject constructor(store: PreferenceStore) {

    /** Master toggle for the NovelUpdates metadata/recommendations section. */
    val enabled = store.getBoolean("novelupdates_enabled", false)

    /**
     * Manual novel -> NovelUpdates slug overrides. Keyed by "pkg|url" so a link
     * survives across navigation and app restarts even before a tracker DB
     * table exists (Phase 2 promotes these to a Room table).
     */
    val manualLinks = store.getObject(
        key = "novelupdates_manual_links",
        defaultValue = emptyMap(),
        serialize = ::serializeNuLinks,
        deserialize = ::deserializeNuLinks,
    )
}

// Control chars that cannot appear in package names or URLs.
private const val PAIR_SEP = ""
private const val ENTRY_SEP = ""

private fun serializeNuLinks(map: Map<String, String>): String =
    map.entries.joinToString(ENTRY_SEP) { "${it.key}$PAIR_SEP${it.value}" }

private fun deserializeNuLinks(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(ENTRY_SEP).mapNotNull { line ->
        val parts = line.split(PAIR_SEP)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[0] to parts[1]
        } else null
    }.toMap()
}
