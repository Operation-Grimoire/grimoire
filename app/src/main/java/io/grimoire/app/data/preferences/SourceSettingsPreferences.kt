package io.grimoire.app.data.preferences

import io.grimoire.api.model.lang.Language
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-source settings (e.g. login credentials, a mirror domain) for
 * [io.grimoire.api.source.feature.ConfigurableSource] extensions. Values are stored as
 * strings keyed by `source.<packageName>.<prefKey>` and pushed back into the
 * source instance via [io.grimoire.app.extension.ExtensionManager].
 */
@Singleton
class SourceSettingsPreferences @Inject constructor(
    private val store: PreferenceStore,
    private val appLanguages: AppLanguagePreferences,
) {
    fun pref(pkg: String, key: String, default: String = ""): Preference<String> =
        store.getString("source.$pkg.$key", default)

    /**
     * Pinned mirror host for a [io.grimoire.api.source.feature.MultiHostSource], as a
     * scheme-qualified origin. Empty means "no override" — the source falls back
     * to its first/default host.
     */
    fun activeHost(pkg: String): Preference<String> =
        store.getString("source.$pkg.active_host", "")

    suspend fun activeHostNow(pkg: String): String = activeHost(pkg).changes().first()

    /** Reads the current value of every [keys] entry for [pkg] as a snapshot map. */
    suspend fun snapshot(pkg: String, keys: List<String>): Map<String, String> =
        keys.associateWith { pref(pkg, it).changes().first() }

    /**
     * Per-source override of the global content-language picker. When `false`
     * (default), [effectiveLanguages] returns the app-wide set; when `true`, it
     * returns the per-source set in [contentLanguages] — letting the user pick
     * a different language mix for just this source.
     */
    fun contentLanguagesOverride(pkg: String): Preference<Boolean> =
        store.getBoolean("source.$pkg.content_languages_override", false)

    /**
     * Per-source content-language selection (only consulted when the override
     * flag is on). Stored as ISO [Language.code]s; empty set means "no filter —
     * show every language". Legacy English-name values are read transparently
     * and rewritten to codes on the next save (see [ContentLanguages.deserialize]).
     */
    fun contentLanguages(pkg: String): Preference<Set<Language>> =
        store.getObject(
            key = "source.$pkg.content_languages",
            defaultValue = emptySet(),
            serialize = { ContentLanguages.serialize(it) },
            deserialize = { ContentLanguages.deserialize(it) },
        )

    suspend fun enabledLanguages(pkg: String): Set<Language> =
        contentLanguages(pkg).changes().first()

    /**
     * The language set that should actually be pushed to a multi-language
     * source: the per-source set when the override flag is on, otherwise the
     * global app-wide set. The only place global-vs-override is resolved.
     */
    suspend fun effectiveLanguages(pkg: String): Set<Language> =
        if (contentLanguagesOverride(pkg).changes().first()) enabledLanguages(pkg)
        else appLanguages.enabledNow()
}
