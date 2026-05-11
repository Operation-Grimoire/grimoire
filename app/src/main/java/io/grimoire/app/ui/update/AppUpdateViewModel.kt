package io.grimoire.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.preferences.AppPreferences
import io.grimoire.app.data.preferences.UpdatePreferences
import io.grimoire.app.data.update.AppUpdateChecker
import io.grimoire.app.data.update.Changelog
import io.grimoire.app.data.update.ReleaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checker: AppUpdateChecker,
    private val appPreferences: AppPreferences,
    private val updatePreferences: UpdatePreferences,
) : ViewModel() {

    private val _changelogText = MutableStateFlow<String?>(null)
    val changelogText: StateFlow<String?> = _changelogText.asStateFlow()

    private val _availableRelease = MutableStateFlow<ReleaseInfo?>(null)
    val availableRelease: StateFlow<ReleaseInfo?> = _availableRelease.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    init {
        viewModelScope.launch {
            val lastSeen = appPreferences.lastSeenVersionCode.changes().first()
            if (lastSeen == 0) {
                // First install — mark without showing changelog
                appPreferences.lastSeenVersionCode.set(BuildConfig.VERSION_CODE)
            } else if (BuildConfig.VERSION_CODE > lastSeen) {
                _changelogText.value = Changelog.since(lastSeen, BuildConfig.VERSION_CODE)
            }
            val channel = updatePreferences.channel.changes().first()
            _availableRelease.value = checker.checkForUpdate(channel)
        }
    }

    fun dismissChangelog() {
        _changelogText.value = null
        viewModelScope.launch { appPreferences.lastSeenVersionCode.set(BuildConfig.VERSION_CODE) }
    }

    fun downloadAndInstall() {
        val release = _availableRelease.value ?: return
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            checker.downloadAndInstall(release.apkUrl)
            _isDownloading.value = false
            _availableRelease.value = null
        }
    }

    fun dismissUpdate() {
        _availableRelease.value = null
    }
}
