package io.grimoire.app.ui.screen.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.AnalyticsPreferences
import io.grimoire.app.data.preferences.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val analyticsPreferences: AnalyticsPreferences,
) : ViewModel() {

    val crashReportsEnabled: StateFlow<Boolean> =
        analyticsPreferences.crashReportsEnabled.stateIn(viewModelScope)
    val usageAnalyticsEnabled: StateFlow<Boolean> =
        analyticsPreferences.usageAnalyticsEnabled.stateIn(viewModelScope)

    fun setCrashReports(value: Boolean) = viewModelScope.launch {
        analyticsPreferences.crashReportsEnabled.set(value)
    }
    fun setUsageAnalytics(value: Boolean) = viewModelScope.launch {
        analyticsPreferences.usageAnalyticsEnabled.set(value)
    }
}
