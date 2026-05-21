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

// Eagerly: preferences pre-warm as soon as the ViewModel is created, so subpages
// don't render the default value for a frame before the persisted value arrives.
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
