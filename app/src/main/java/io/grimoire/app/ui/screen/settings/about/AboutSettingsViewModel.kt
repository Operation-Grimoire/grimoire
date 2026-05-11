package io.grimoire.app.ui.screen.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.UpdatePreferences
import io.grimoire.app.data.update.AppUpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val version: String, val apkUrl: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    private val checker: AppUpdateChecker,
    private val updatePreferences: UpdatePreferences,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            runCatching {
                val channel = updatePreferences.channel.changes().first()
                val release = checker.checkForUpdate(channel)
                if (release != null) {
                    _updateState.value = UpdateState.Available(release.displayVersion, release.apkUrl)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        viewModelScope.launch { checker.downloadAndInstall(apkUrl) }
    }
}
