package io.grimoire.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStorePreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferenceStore {

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        PrimitivePreference(dataStore, booleanPreferencesKey(key), defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        PrimitivePreference(dataStore, intPreferencesKey(key), defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        PrimitivePreference(dataStore, longPreferencesKey(key), defaultValue)

    override fun getString(key: String, defaultValue: String): Preference<String> =
        PrimitivePreference(dataStore, stringPreferencesKey(key), defaultValue)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
    ): Preference<T> = ObjectPreference(dataStore, stringPreferencesKey(key), defaultValue, serialize, deserialize)
}

private class PrimitivePreference<T>(
    private val dataStore: DataStore<Preferences>,
    private val prefKey: Preferences.Key<T>,
    private val default: T,
) : Preference<T> {
    override fun key() = prefKey.name
    override fun defaultValue() = default
    override fun changes(): Flow<T> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[prefKey] ?: default }
    override suspend fun set(value: T) { dataStore.edit { it[prefKey] = value } }
}

private class ObjectPreference<T>(
    private val dataStore: DataStore<Preferences>,
    private val prefKey: Preferences.Key<String>,
    private val default: T,
    private val serialize: (T) -> String,
    private val deserialize: (String) -> T?,
) : Preference<T> {
    override fun key() = prefKey.name
    override fun defaultValue() = default
    override fun changes(): Flow<T> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[prefKey]?.let { deserialize(it) } ?: default }
    override suspend fun set(value: T) { dataStore.edit { it[prefKey] = serialize(value) } }
}
