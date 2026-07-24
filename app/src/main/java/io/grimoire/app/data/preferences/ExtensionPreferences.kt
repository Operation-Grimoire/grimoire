package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionPreferences @Inject constructor(store: PreferenceStore) {

    /**
     * Uppercase language codes to show in the Extensions list. An **empty set
     * means "show all"** (the default), so a fresh install lists every language;
     * the user narrows it to the languages they read. Stored newline-joined.
     */
    val enabledLanguages: Preference<Set<String>> = store.getObject(
        key = "extensions_enabled_languages",
        defaultValue = emptySet(),
        serialize = { it.joinToString("\n") },
        deserialize = { raw -> raw.split("\n").filter { it.isNotBlank() }.toSet() },
    )
}
