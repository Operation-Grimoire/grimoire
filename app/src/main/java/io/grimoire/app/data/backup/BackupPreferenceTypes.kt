package io.grimoire.app.data.backup

import io.grimoire.app.data.preferences.AnalyticsPreferences
import io.grimoire.app.data.preferences.AppLanguagePreferences
import io.grimoire.app.data.preferences.AppPreferences
import io.grimoire.app.data.preferences.BackupPreferences
import io.grimoire.app.data.preferences.BrowsePreferences
import io.grimoire.app.data.preferences.DownloadPreferences
import io.grimoire.app.data.preferences.HiddenCategoriesPreferences
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.data.preferences.NovelUpdatesPreferences
import io.grimoire.app.data.preferences.OnboardingPreferences
import io.grimoire.app.data.preferences.ReaderPreferences
import io.grimoire.app.data.preferences.TtsPreferences
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.data.preferences.UpdatePreferences
import io.grimoire.app.data.preferences.Preference
import io.grimoire.app.data.preferences.PreferenceStore
import kotlinx.coroutines.flow.flowOf

// Read the actual declarations so future type changes also change restore validation.
private val declaredPreferenceTypes: Map<String, String> by lazy {
    val store = PreferenceTypeCollector()
    AnalyticsPreferences(store)
    AppLanguagePreferences(store)
    AppPreferences(store)
    BackupPreferences(store)
    BrowsePreferences(store)
    DownloadPreferences(store)
    HiddenCategoriesPreferences(store)
    LibraryPreferences(store)
    LibraryUpdatePreferences(store)
    NovelUpdatesPreferences(store)
    OnboardingPreferences(store)
    ReaderPreferences(store)
    TtsPreferences(store)
    UiPreferences(store)
    UpdatePreferences(store)
    store.types + mapOf(
        "schedule_prefs_migrated_v1" to "b",
        "source_ids_pkg_derived_v1" to "b",
        "backup_frequency" to "s",
        "library_update_frequency" to "s",
    )
}

internal fun expectedBackupPreferenceType(key: String): String? = when {
    key.startsWith("source.") -> if (key.endsWith(".content_languages_override")) "b" else "s"
    key.startsWith("tour_completed_") -> "i"
    else -> declaredPreferenceTypes[key]
}

internal fun storedPreferenceType(value: Any?): String? = when (value) {
    is Boolean -> "b"
    is Int -> "i"
    is Long -> "l"
    is Float -> "f"
    is Double -> "d"
    is String -> "s"
    is Set<*> -> "ss"
    else -> null
}

private class PreferenceTypeCollector : PreferenceStore {
    val types = mutableMapOf<String, String>()

    private fun <T> record(key: String, type: String, default: T): Preference<T> {
        types[key] = type
        return object : Preference<T> {
            override fun key() = key
            override fun defaultValue() = default
            override fun changes() = flowOf(default)
            override suspend fun set(value: T) = error("Preference schema cannot write values")
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean) = record(key, "b", defaultValue)
    override fun getInt(key: String, defaultValue: Int) = record(key, "i", defaultValue)
    override fun getLong(key: String, defaultValue: Long) = record(key, "l", defaultValue)
    override fun getString(key: String, defaultValue: String) = record(key, "s", defaultValue)
    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
    ) = record(key, "s", defaultValue)
}
