package io.grimoire.app.ui.screen.settings.libraryupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.data.schedule.SCHEDULE_MAX_COUNT
import io.grimoire.app.data.schedule.SCHEDULE_MIN_COUNT
import io.grimoire.app.data.schedule.ScheduleUnit
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

    val enabled = preferences.enabled.stateIn(viewModelScope)
    val intervalCount = preferences.intervalCount.stateIn(viewModelScope)
    val intervalUnit = preferences.intervalUnit.stateIn(viewModelScope)
    val onlyOnWifi = preferences.onlyOnWifi.stateIn(viewModelScope)
    val requiresCharging = preferences.requiresCharging.stateIn(viewModelScope)
    val autoDownloadNewChapters = preferences.autoDownloadNewChapters.stateIn(viewModelScope)
    val concurrency = preferences.concurrency.stateIn(viewModelScope)
    val preferredTimeOfDayMinutes = preferences.preferredTimeOfDayMinutes.stateIn(viewModelScope)
    val lastRunAt = preferences.lastRunAt.changes()
        .map { it.toLongOrNull() ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val lastRunSuccess = preferences.lastRunSuccess.stateIn(viewModelScope)
    val lastRunMessage = preferences.lastRunMessage.stateIn(viewModelScope)

    fun setEnabled(value: Boolean) = viewModelScope.launch {
        preferences.enabled.set(value)
    }

    fun setIntervalCount(value: Int) = viewModelScope.launch {
        preferences.intervalCount.set(value.coerceIn(SCHEDULE_MIN_COUNT, SCHEDULE_MAX_COUNT))
    }

    fun setIntervalUnit(value: ScheduleUnit) = viewModelScope.launch {
        preferences.intervalUnit.set(value)
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

    fun setPreferredTimeOfDay(hour: Int, minute: Int) = viewModelScope.launch {
        val minutes = (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59))
        preferences.preferredTimeOfDayMinutes.set(minutes)
    }

    fun updateLibraryNow() = scheduler.triggerOneOff(null)
}
