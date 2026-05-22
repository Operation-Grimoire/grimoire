package io.grimoire.app.ui.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped holder for the manual "check for updates" result.
 *
 * Keeping it here rather than in [AppUpdateViewModel] means an `UpToDate`
 * outcome survives the ViewModel being recreated: the checkmark stays and
 * re-checking stays disabled until the app process restarts.
 */
@Singleton
class AppUpdateCheckStore @Inject constructor() {
    private val _state = MutableStateFlow<CheckState>(CheckState.Idle)
    val state: StateFlow<CheckState> = _state.asStateFlow()

    fun set(value: CheckState) {
        _state.value = value
    }
}
