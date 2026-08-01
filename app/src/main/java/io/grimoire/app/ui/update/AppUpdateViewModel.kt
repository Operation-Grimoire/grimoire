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

sealed interface ChangelogContent {
    data class Notes(val text: String) : ChangelogContent
    data object Unavailable : ChangelogContent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: AppUpdateChecker,
    private val appPreferences: AppPreferences,
    private val updatePreferences: UpdatePreferences,
    private val downloadStore: AppUpdateDownloadStore,
    private val checkStore: AppUpdateCheckStore,
) : ViewModel() {

    private val _changelogContent = MutableStateFlow<ChangelogContent?>(null)
    val changelogContent: StateFlow<ChangelogContent?> = _changelogContent.asStateFlow()

    private val _availableRelease = MutableStateFlow<ReleaseInfo?>(null)
    val availableRelease: StateFlow<ReleaseInfo?> = _availableRelease.asStateFlow()

    // Download progress lives in a process-wide store so it survives this
    // ViewModel being cleared while the foreground service keeps downloading.
    val downloadState: StateFlow<DownloadState> = downloadStore.state

    // The check result lives in a process-scoped store so an UpToDate outcome
    // survives this ViewModel being recreated.
    val checkState: StateFlow<CheckState> = checkStore.state

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
                // The user already read these notes in the update-available
                // dialog when they started this update — don't repeat them.
                val alreadySeen =
                    updatePreferences.changelogSeenVersion.changes().first() == BuildConfig.VERSION_NAME
                if (alreadySeen) {
                    appPreferences.lastSeenVersionCode.set(BuildConfig.VERSION_CODE)
                    appPreferences.lastSeenVersionName.set(BuildConfig.VERSION_NAME)
                } else if (autoChangelogEnabled) {
                    val prevName = appPreferences.lastSeenVersionName.changes().first()
                    val hasPrev = prevName.isNotBlank() && prevName != BuildConfig.VERSION_NAME
                    val remote: String? = when (channel) {
                        UpdateChannel.STABLE -> {
                            if (hasPrev) {
                                checker.fetchStableNotesSince(prevName, BuildConfig.VERSION_NAME)
                            } else {
                                checker.fetchNotesForVersion(BuildConfig.VERSION_NAME)
                            }
                        }
                        UpdateChannel.BETA -> {
                            if (hasPrev) {
                                checker.fetchBetaNotesSince(prevName, BuildConfig.VERSION_NAME)
                            } else {
                                checker.fetchNotesForVersion(BuildConfig.VERSION_NAME)
                            }
                        }
                    }
                    _changelogContent.value = (remote ?: Changelog.since(
                        lastSeen,
                        BuildConfig.VERSION_CODE,
                    ))?.let(ChangelogContent::Notes)
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
        if (checkStore.state.value is CheckState.Checking) return
        viewModelScope.launch {
            checkStore.set(CheckState.Checking)
            runCatching {
                val channel = updatePreferences.channel.changes().first()
                val release = checker.checkForUpdate(channel)
                if (release != null) {
                    _availableRelease.value = release
                    checkStore.set(CheckState.Idle)
                } else {
                    checkStore.set(CheckState.UpToDate)
                }
            }.onFailure { e ->
                checkStore.set(CheckState.Error(e.message.orEmpty()))
            }
        }
    }

    /** Allows a fresh check — e.g. after the update channel changes. */
    fun resetCheckState() {
        checkStore.set(CheckState.Idle)
    }

    fun showCurrentChangelog() {
        if (_changelogContent.value != null || _isLoadingChangelog.value) return
        viewModelScope.launch {
            _isLoadingChangelog.value = true
            try {
                val remote = checker.fetchNotesForVersion(BuildConfig.VERSION_NAME)
                _changelogContent.value = remote?.let(ChangelogContent::Notes)
                    ?: ChangelogContent.Unavailable
            } finally {
                _isLoadingChangelog.value = false
            }
        }
    }

    fun dismissChangelog() {
        _changelogContent.value = null
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
        // The update dialog is showing this release's notes right now — record
        // that so the post-install "what's new" popup doesn't repeat them.
        viewModelScope.launch {
            updatePreferences.changelogSeenVersion.set(release.tagName.removePrefix("v"))
        }
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
