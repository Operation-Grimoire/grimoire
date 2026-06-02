package io.grimoire.app.ui.screen.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.backup.BackupManager
import io.grimoire.app.data.backup.BackupResult
import io.grimoire.app.data.backup.BackupScheduler
import io.grimoire.app.data.backup.RestoreResult
import io.grimoire.app.data.preferences.BackupPreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.data.schedule.SCHEDULE_MAX_COUNT
import io.grimoire.app.data.schedule.SCHEDULE_MIN_COUNT
import io.grimoire.app.data.schedule.ScheduleUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupUiEvent {
    data class Info(val message: String) : BackupUiEvent()
    data class Error(val message: String) : BackupUiEvent()
}

data class BackupUiState(
    val running: Boolean = false,
    val event: BackupUiEvent? = null,
)

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val backupScheduler: BackupScheduler,
    private val backupPreferences: BackupPreferences,
) : ViewModel() {

    val folderUri = backupPreferences.backupFolderUri.stateIn(viewModelScope)
    val enabled = backupPreferences.enabled.stateIn(viewModelScope)
    val intervalCount = backupPreferences.intervalCount.stateIn(viewModelScope)
    val intervalUnit = backupPreferences.intervalUnit.stateIn(viewModelScope)
    val preferredTimeOfDayMinutes = backupPreferences.preferredTimeOfDayMinutes.stateIn(viewModelScope)
    val onlyOnWifi = backupPreferences.onlyOnWifi.stateIn(viewModelScope)
    val requiresCharging = backupPreferences.requiresCharging.stateIn(viewModelScope)
    val lastAutoBackupAt = backupPreferences.lastAutoBackupAt.changes()
        .map { it.toLongOrNull() ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val lastAutoBackupFile = backupPreferences.lastAutoBackupFile.stateIn(viewModelScope)
    val lastAutoBackupSuccess = backupPreferences.lastAutoBackupSuccess.stateIn(viewModelScope)
    val lastAutoBackupMessage = backupPreferences.lastAutoBackupMessage.stateIn(viewModelScope)

    private val _ui = MutableStateFlow(BackupUiState())
    val ui: StateFlow<BackupUiState> = _ui.asStateFlow()

    fun setFolderUri(uri: String) = viewModelScope.launch {
        backupPreferences.backupFolderUri.set(uri)
    }

    fun setEnabled(value: Boolean) = viewModelScope.launch {
        backupPreferences.enabled.set(value)
    }

    fun setIntervalCount(value: Int) = viewModelScope.launch {
        backupPreferences.intervalCount.set(value.coerceIn(SCHEDULE_MIN_COUNT, SCHEDULE_MAX_COUNT))
    }

    fun setIntervalUnit(value: ScheduleUnit) = viewModelScope.launch {
        backupPreferences.intervalUnit.set(value)
    }

    fun setPreferredTimeOfDay(hour: Int, minute: Int) = viewModelScope.launch {
        val minutes = (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59))
        backupPreferences.preferredTimeOfDayMinutes.set(minutes)
    }

    fun setOnlyOnWifi(value: Boolean) = viewModelScope.launch {
        backupPreferences.onlyOnWifi.set(value)
    }

    fun setRequiresCharging(value: Boolean) = viewModelScope.launch {
        backupPreferences.requiresCharging.set(value)
    }

    fun backupNow(folderUri: Uri) = viewModelScope.launch {
        _ui.value = _ui.value.copy(running = true, event = null)
        when (val result = backupManager.backupTo(folderUri)) {
            is BackupResult.Success ->
                _ui.value = BackupUiState(event = BackupUiEvent.Info(
                    "Saved ${result.fileName} (${result.novelCount} novels)"
                ))
            is BackupResult.Failure ->
                _ui.value = BackupUiState(event = BackupUiEvent.Error(result.message))
        }
    }

    fun restoreFrom(fileUri: Uri) = viewModelScope.launch {
        _ui.value = _ui.value.copy(running = true, event = null)
        when (val result = backupManager.restoreFrom(fileUri)) {
            is RestoreResult.Success ->
                _ui.value = BackupUiState(event = BackupUiEvent.Info(
                    "Restored ${result.novelCount} novels, ${result.chapterCount} chapters"
                ))
            is RestoreResult.Failure ->
                _ui.value = BackupUiState(event = BackupUiEvent.Error(result.message))
        }
    }

    fun triggerScheduledBackupNow() {
        backupScheduler.triggerOneOffNow()
        _ui.value = BackupUiState(event = BackupUiEvent.Info("Backup queued"))
    }

    fun consumeEvent() {
        _ui.value = _ui.value.copy(event = null)
    }
}
