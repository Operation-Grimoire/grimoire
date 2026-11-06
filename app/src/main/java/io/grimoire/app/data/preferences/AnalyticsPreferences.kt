package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

internal const val CRASH_REPORTS_KEY = "diagnostics_crash_reports"
internal const val USAGE_ANALYTICS_KEY = "diagnostics_usage_analytics"

/**
 * Diagnostics preferences. Both default **on** and are opt-out from Settings →
 * Privacy; anonymous data is still only sent when the corresponding build key is
 * configured. Users who previously turned a toggle off keep that stored choice.
 */
@Singleton
class AnalyticsPreferences @Inject constructor(store: PreferenceStore) {

    /** Send anonymous crash reports (Aptabase), independently of usage analytics. */
    val crashReportsEnabled = store.getBoolean(CRASH_REPORTS_KEY, true, readErrorValue = false)

    /** Send anonymous usage analytics (Aptabase). */
    val usageAnalyticsEnabled = store.getBoolean(USAGE_ANALYTICS_KEY, true, readErrorValue = false)
}
