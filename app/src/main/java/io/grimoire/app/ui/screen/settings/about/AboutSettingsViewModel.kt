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

    fun setChannel(channel: UpdateChannel) = viewModelScope.launch {
        updatePreferences.channel.set(channel)
    }
}
