package io.grimoire.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.AnalyticsPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsConsentViewModel @Inject constructor(
    private val analyticsPreferences: AnalyticsPreferences,
) : ViewModel() {

    /** null until the persisted value loads, so the gate doesn't flash the consent
     *  screen for a frame before disk reports the user already answered. */
    val prompted: StateFlow<Boolean?> = analyticsPreferences.prompted.changes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Persist the user's choices and mark the prompt answered. */
    fun save(crashReports: Boolean, usageAnalytics: Boolean) = viewModelScope.launch {
        analyticsPreferences.crashReportsEnabled.set(crashReports)
        analyticsPreferences.usageAnalyticsEnabled.set(usageAnalytics)
        analyticsPreferences.prompted.set(true)
    }
}
