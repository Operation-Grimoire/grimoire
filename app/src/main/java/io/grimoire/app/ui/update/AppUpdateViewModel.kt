package io.grimoire.app.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.preferences.AppPreferences
import io.grimoire.app.data.preferences.UpdateChannel
import io.grimoire.app.data.preferences.UpdatePreferences
import io.grimoire.app.data.update.AppUpdateChecker
import io.grimoire.app.data.update.AppUpdateDownloadStore
import io.grimoire.app.data.update.AppUpdateService
import io.grimoire.app.data.update.Changelog
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
    private val appPreferences: AppPreferences,
    private val updatePreferences: UpdatePreferences,
    private val downloadStore: AppUpdateDownloadStore,
) : ViewModel() {

    private val _changelogText = MutableStateFlow<String?>(null)
    val changelogText: StateFlow<String?> = _changelogText.asStateFlow()

    private val _availableRelease = MutableStateFlow<ReleaseInfo?>(null)
    val availableRelease: StateFlow<ReleaseInfo?> = _availableRelease.asStateFlow()

    // Download progress lives in a process-wide store so it survives this
    // ViewModel being cleared while the foreground service keeps downloading.
    val downloadState: StateFlow<DownloadState> = downloadStore.state

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState: StateFlow<CheckState> = _checkState.asStateFlow()

    private val _isLoadingChangelog = MutableStateFlow(false)
    val isLoadingChangelog: StateFlow<Boolean> = _isLoadingChangelog.asStateFlow()

    init {
        viewModelScope.launch {
            val lastSeen = appPreferences.lastSeenVersionCode.changes().first()
            val channel = updatePreferences.channel.changes().first()
            if (lastSeen == 0) {
                // First install — mark without showing changelog.
                appPreferences.lastSeenVersionCode.set(BuildConfig.VERSION_CODE)
                appPreferences.lastSeenVersionName.set(BuildConfig.VERSION_NAME)
            } else if (BuildConfig.VERSION_CODE > lastSeen) {
                val autoChangelogEnabled = updatePreferences.autoChangelogEnabled.changes().first()
                if (autoChangelogEnabled) {
                    val remote: String? = when (channel) {
                        UpdateChannel.STABLE -> {
                            val prevName = appPreferences.lastSeenVersionName.changes().first()
                            if (prevName.isNotBlank() && prevName != BuildConfig.VERSION_NAME) {
                                checker.fetchStableNotesSince(prevName, BuildConfig.VERSION_NAME)
                            } else {
                                checker.fetchStableNotesForVersion(BuildConfig.VERSION_NAME)
                            }
                        }
                        UpdateChannel.BETA -> checker.fetchBetaNotesForSha(BuildConfig.GIT_SHA)
                    }
                    _changelogText.value = remote ?: Changelog.since(lastSeen, BuildConfig.VERSION_CODE)
                } else {
                    // Popup disabled — mark this version as seen so we don't
                    // accumulate fetches if the user re-enables it later.
                    // The user can still open the dialog manually via
                    // showCurrentChangelog().
                    appPreferences.lastSeenVersionCode.set(BuildConfig.VERSION_CODE)
                    appPreferences.lastSeenVersionName.set(BuildConfig.VERSION_NAME)
                }
            }
            val release = checker.checkForUpdate(channel) ?: return@launch
            val autoPopupEnabled = updatePreferences.autoPopupEnabled.changes().first()
            val skippedVersion = updatePreferences.skippedVersion.changes().first()
            if (!autoPopupEnabled || release.tagName == skippedVersion) return@launch
            _availableRelease.value = release
        }
    }

    fun checkForUpdates() {
        if (_checkState.value is CheckState.Checking) return
        viewModelScope.launch {
            _checkState.value = CheckState.Checking
            runCatching {
                val channel = updatePreferences.channel.changes().first()
                val release = checker.checkForUpdate(channel)
                if (release != null) {
                    _availableRelease.value = release
                    _checkState.value = CheckState.Idle
                } else {
                    _checkState.value = CheckState.UpToDate
                }
            }.onFailure { e ->
                _checkState.value = CheckState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetCheckState() {
        _checkState.value = CheckState.Idle
    }

    fun showCurrentChangelog() {
        if (_changelogText.value != null || _isLoadingChangelog.value) return
        viewModelScope.launch {
            _isLoadingChangelog.value = true
            try {
                val channel = updatePreferences.channel.changes().first()
                val remote = when (channel) {
                    UpdateChannel.STABLE -> checker.fetchStableNotesForVersion(BuildConfig.VERSION_NAME)
                    UpdateChannel.BETA -> checker.fetchBetaNotesForSha(BuildConfig.GIT_SHA)
                }
                _changelogText.value = remote
                    ?: "No changelog available for ${BuildConfig.VERSION_NAME}."
            } finally {
                _isLoadingChangelog.value = false
            }
        }
    }

    fun dismissChangelog() {
        _changelogText.value = null
        viewModelScope.launch {
            appPreferences.lastSeenVersionCode.set(BuildConfig.VERSION_CODE)
            appPreferences.lastSeenVersionName.set(BuildConfig.VERSION_NAME)
        }
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
