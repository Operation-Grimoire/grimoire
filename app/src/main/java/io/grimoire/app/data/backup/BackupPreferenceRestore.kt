package io.grimoire.app.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal fun restoreBackupPreferences(prefs: MutablePreferences, preferences: List<BackupPreference>) {
    for (p in preferences) {
        if (p.key.startsWith(AUTOMATIC_BACKUP_REGISTRY_PREFIX)) continue
        when (p.type) {
            "b" -> p.value?.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(p.key)] = it }
            "i" -> p.value?.toIntOrNull()?.let { prefs[intPreferencesKey(p.key)] = it }
            "l" -> p.value?.toLongOrNull()?.let { prefs[longPreferencesKey(p.key)] = it }
            "f" -> p.value?.toFloatOrNull()?.let { prefs[floatPreferencesKey(p.key)] = it }
            "d" -> p.value?.toDoubleOrNull()?.let { prefs[doublePreferencesKey(p.key)] = it }
            "s" -> p.value?.let { prefs[stringPreferencesKey(p.key)] = it }
            "ss" -> p.stringSet?.let { prefs[stringSetPreferencesKey(p.key)] = it.toSet() }
        }
    }
}
