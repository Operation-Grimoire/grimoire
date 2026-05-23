package io.grimoire.app.ui.screen.settings.libraryupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.preferences.LibraryUpdateFrequency
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.data.preferences.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryUpdateSettingsViewModel @Inject constructor(
    private val preferences: LibraryUpdatePreferences,
    private val scheduler: LibraryUpdateScheduler,
) : ViewModel() {

    val frequency = preferences.frequency.stateIn(viewModelScope)
    val onlyOnWifi = preferences.onlyOnWifi.stateIn(viewModelScope)
    val requiresCharging = preferences.requiresCharging.stateIn(viewModelScope)
    val autoDownloadNewChapters = preferences.autoDownloadNewChapters.stateIn(viewModelScope)
    val concurrency = preferences.concurrency.stateIn(viewModelScope)
    val lastRunAt = preferences.lastRunAt.changes()
        .map { it.toLongOrNull() ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val lastRunSuccess = preferences.lastRunSuccess.stateIn(viewModelScope)
    val lastRunMessage = preferences.lastRunMessage.stateIn(viewModelScope)

    fun setFrequency(value: LibraryUpdateFrequency) = viewModelScope.launch {
        preferences.frequency.set(value)
    }

    fun setOnlyOnWifi(value: Boolean) = viewModelScope.launch {
        preferences.onlyOnWifi.set(value)
    }

    fun setRequiresCharging(value: Boolean) = viewModelScope.launch {
        preferences.requiresCharging.set(value)
    }

    fun setAutoDownloadNewChapters(value: Boolean) = viewModelScope.launch {
        preferences.autoDownloadNewChapters.set(value)
    }

    fun setConcurrency(value: Int) = viewModelScope.launch {
        preferences.concurrency.set(value.coerceIn(1, 8))
    }

    fun updateLibraryNow() = scheduler.triggerOneOff(null)
}
