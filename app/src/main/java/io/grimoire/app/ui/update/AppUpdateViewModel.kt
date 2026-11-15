package io.grimoire.app.ui.update

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.preferences.UpdateChannel
import io.grimoire.app.data.preferences.UpdatePreferences
import io.grimoire.app.data.update.AppUpdateChecker
import io.grimoire.app.data.update.AppUpdateDownloadStore
import io.grimoire.app.data.update.AppUpdateService
import io.grimoire.app.data.update.DownloadState
import io.grimoire.app.data.update.ReleaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CheckState {
    data object Idle : CheckState()
    data object Checking : CheckState()
    data object UpToDate : CheckState()
    data class Error(val message: String) : CheckState()
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: AppUpdateChecker,
    private val updatePreferences: UpdatePreferences,
    private val downloadStore: AppUpdateDownloadStore,
    private val checkStore: AppUpdateCheckStore,
) : ViewModel() {

    private val _availableRelease = MutableStateFlow<ReleaseInfo?>(null)
    val availableRelease: StateFlow<ReleaseInfo?> = _availableRelease.asStateFlow()

    // Download progress lives in a process-wide store so it survives this
    // ViewModel being cleared while the foreground service keeps downloading.
    val downloadState: StateFlow<DownloadState> = downloadStore.state

    // The check result lives in a process-scoped store so an UpToDate outcome
    // survives this ViewModel being recreated.
    val checkState: StateFlow<CheckState> = checkStore.state

    init {
        viewModelScope.launch {
            val autoPopupEnabled = updatePreferences.autoPopupEnabled.changes().first()
            if (!autoPopupEnabled) return@launch
            val channel = updatePreferences.channel.changes().first()
            val release = checker.checkForUpdate(channel).getOrNull() ?: return@launch
            val skippedVersion = updatePreferences.skippedVersion.changes().first()
            if (release.tagName == skippedVersion) return@launch
            _availableRelease.value = release
        }
    }

    fun checkForUpdates() {
        if (checkStore.state.value is CheckState.Checking) return
        viewModelScope.launch {
            checkStore.set(CheckState.Checking)
            runCatching {
                val channel = updatePreferences.channel.changes().first()
                val release = checker.checkForUpdate(channel).getOrThrow()
                if (release != null) {
                    _availableRelease.value = release
                    checkStore.set(CheckState.Idle)
                } else {
                    checkStore.set(CheckState.UpToDate)
                }
            }.onFailure { e ->
                Log.e("AppUpdateVM", "Update check failed", e)
                // The screen already has a translated fallback for an empty error message.
                checkStore.set(CheckState.Error(""))
            }
        }
    }

    /** Allows a fresh check — e.g. after the update channel changes. */
    fun resetCheckState() {
        checkStore.set(CheckState.Idle)
    }

    /**
     * Hands the download to a foreground service so it keeps running even if
     * the app is closed before it finishes. Progress and completion surface
     * through [downloadState] and an ongoing notification.
     */
    fun startDownload() {
        val release = _availableRelease.value ?: return
        if (downloadState.value is DownloadState.Downloading) return
        downloadStore.set(DownloadState.Downloading(0L, 0L))
        AppUpdateService.start(context, release)
    }

    fun installUpdate() {
        val state = downloadState.value
        if (state is DownloadState.Completed) {
            checker.launchInstall(state.file)
        }
    }

    fun dismissUpdate() {
        // The download keeps running in the background service when dismissed
        // mid-download; only clear finished or failed states.
        if (downloadState.value !is DownloadState.Downloading) {
            downloadStore.set(DownloadState.Idle)
        }
        _availableRelease.value = null
    }

    fun skipVersion() {
        if (downloadState.value is DownloadState.Downloading) return
        val release = _availableRelease.value ?: return
        viewModelScope.launch { updatePreferences.skippedVersion.set(release.tagName) }
        downloadStore.set(DownloadState.Idle)
        _availableRelease.value = null
    }
}
