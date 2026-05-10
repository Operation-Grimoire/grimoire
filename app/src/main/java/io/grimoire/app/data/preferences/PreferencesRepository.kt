package io.grimoire.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val appPreferences: Flow<AppPreferences> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppPreferences(
                themeMode = prefs[THEME_MODE_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: true,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("use_dynamic_color")
    }
}
