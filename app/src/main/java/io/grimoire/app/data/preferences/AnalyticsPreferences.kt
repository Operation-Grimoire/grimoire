package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostics preferences. Both default **on** and are opt-out from Settings →
 * Privacy; anonymous data is still only sent when the corresponding build key is
 * configured. Users who previously turned a toggle off keep that stored choice.
 */
@Singleton
class AnalyticsPreferences @Inject constructor(store: PreferenceStore) {

    /** Send anonymous crash reports (GlitchTip). */
    val crashReportsEnabled = store.getBoolean("diagnostics_crash_reports", true)

    /** Send anonymous usage analytics (Aptabase). */
    val usageAnalyticsEnabled = store.getBoolean("diagnostics_usage_analytics", true)
}
