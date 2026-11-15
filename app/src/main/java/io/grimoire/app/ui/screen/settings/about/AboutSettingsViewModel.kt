package io.grimoire.app.ui.screen.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.UpdateChannel
import io.grimoire.app.data.preferences.UpdatePreferences
import io.grimoire.app.data.preferences.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    private val updatePreferences: UpdatePreferences,
) : ViewModel() {

    val channel = updatePreferences.channel.stateIn(viewModelScope)
    val autoPopupEnabled = updatePreferences.autoPopupEnabled.stateIn(viewModelScope)

    fun setChannel(channel: UpdateChannel) = viewModelScope.launch {
        updatePreferences.channel.set(channel)
    }

    fun setAutoPopupEnabled(enabled: Boolean) = viewModelScope.launch {
        updatePreferences.autoPopupEnabled.set(enabled)
        // Re-enabling clears any previously skipped version so the user is
        // prompted again on the next launch.
        if (enabled) updatePreferences.skippedVersion.set("")
    }
}
