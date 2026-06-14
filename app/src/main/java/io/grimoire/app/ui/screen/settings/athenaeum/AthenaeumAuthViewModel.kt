package io.grimoire.app.ui.screen.settings.athenaeum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.auth.athenaeum.AthenaeumAuthRepository
import io.grimoire.app.auth.athenaeum.AthenaeumAuthState
import io.grimoire.app.data.preferences.AthenaeumPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AthenaeumAuthViewModel @Inject constructor(
    private val repository: AthenaeumAuthRepository,
    private val preferences: AthenaeumPreferences,
) : ViewModel() {
    val state: StateFlow<AthenaeumAuthState> = repository.state

    val contributeEnabled: StateFlow<Boolean> = preferences.contributeEnabled.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The app submits scraped catalogue data, so it requests ingest:write. */
    fun pair() = repository.pair(setOf("ingest:write"))
    fun cancel() = repository.cancel()
    fun unpair() = repository.unpair()

    fun setContribute(enabled: Boolean) {
        viewModelScope.launch { preferences.contributeEnabled.set(enabled) }
    }
}
