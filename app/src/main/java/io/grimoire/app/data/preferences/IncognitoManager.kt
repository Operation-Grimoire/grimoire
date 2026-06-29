package io.grimoire.app.data.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-scoped incognito switch. While [enabled] is true, neither reading nor browsing
 * history is recorded. Intentionally **not** persisted (no DataStore): the flag lives only
 * in this process-singleton, so it resets to off whenever the app process is recreated.
 */
@Singleton
class IncognitoManager @Inject constructor() {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(value: Boolean) {
        _enabled.value = value
    }

    fun toggle() {
        _enabled.value = !_enabled.value
    }
}
