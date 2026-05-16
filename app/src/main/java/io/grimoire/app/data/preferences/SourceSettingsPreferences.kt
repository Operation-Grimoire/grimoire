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
}
