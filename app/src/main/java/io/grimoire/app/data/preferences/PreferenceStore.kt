package io.grimoire.app.data.preferences

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

interface Preference<T> {
    fun key(): String
    fun defaultValue(): T
    fun changes(): Flow<T>
    suspend fun set(value: T)
}

// Eagerly: collection starts as soon as the ViewModel is created, which narrows —
// but does NOT close — the window where readers see defaultValue(). The persisted
// value still arrives asynchronously from disk, so a screen composed in the same
// frame the ViewModel is created can render the default first. Only screens whose
// ViewModel predates them (e.g. settings subpages sharing SettingsViewModel) are
// guaranteed warm values. Anything that must not act on a default needs an
// explicit loading sentinel (see LibraryViewModel.persistedCategoryId).
fun <T> Preference<T>.stateIn(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.Eagerly,
): StateFlow<T> = changes().stateIn(scope, started, defaultValue())

interface PreferenceStore {
    fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean>
    fun getInt(key: String, defaultValue: Int): Preference<Int>
    fun getLong(key: String, defaultValue: Long): Preference<Long>
    fun getString(key: String, defaultValue: String): Preference<String>
    fun <T> getObject(
        key: String,
        defaultValue: T,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
    ): Preference<T>
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnum(
    key: String,
    defaultValue: T,
): Preference<T> = getObject(
    key = key,
    defaultValue = defaultValue,
    serialize = { it.name },
    deserialize = { runCatching { enumValueOf<T>(it) }.getOrNull() },
)
