package io.grimoire.app.data.preferences

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's global "enabled content languages" preference, applied to every
 * multi-language source by default. A multi-language source can opt out via
 * its own override flag (see [SourceSettingsPreferences.contentLanguagesOverride]).
 *
 * Stored as lowercased English language names (e.g. "english", "spanish"); an
 * empty set means "no filter — show every language".
 */
@Singleton
class AppLanguagePreferences @Inject constructor(
    store: PreferenceStore,
) {
    val enabled: Preference<Set<String>> = store.getObject(
        key = "app.content_languages",
        defaultValue = emptySet(),
        serialize = { it.joinToString(",") },
        deserialize = { raw ->
            if (raw.isBlank()) emptySet()
            else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        },
    )

    suspend fun enabledNow(): Set<String> = enabled.changes().first()
}
