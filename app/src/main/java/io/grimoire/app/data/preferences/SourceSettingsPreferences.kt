package io.grimoire.app.data.preferences

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-source settings (e.g. login credentials, a mirror domain) for
 * [io.grimoire.api.source.ConfigurableSource] extensions. Values are stored as
 * strings keyed by `source.<packageName>.<prefKey>` and pushed back into the
 * source instance via [io.grimoire.app.extension.ExtensionManager].
 */
@Singleton
class SourceSettingsPreferences @Inject constructor(
    private val store: PreferenceStore,
) {
    fun pref(pkg: String, key: String, default: String = ""): Preference<String> =
        store.getString("source.$pkg.$key", default)

    /** Reads the current value of every [keys] entry for [pkg] as a snapshot map. */
    suspend fun snapshot(pkg: String, keys: List<String>): Map<String, String> =
        keys.associateWith { pref(pkg, it).changes().first() }

    /**
     * The set of content languages the user enabled for a multi-language
     * source (`lang == "all"`), stored as lowercase English names. Empty set
     * means "no filter — show every language".
     */
    fun contentLanguages(pkg: String): Preference<Set<String>> =
        store.getObject(
            key = "source.$pkg.content_languages",
            defaultValue = emptySet(),
            serialize = { it.joinToString(",") },
            deserialize = { raw ->
                if (raw.isBlank()) emptySet()
                else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            },
        )

    suspend fun enabledLanguages(pkg: String): Set<String> =
        contentLanguages(pkg).changes().first()
}
