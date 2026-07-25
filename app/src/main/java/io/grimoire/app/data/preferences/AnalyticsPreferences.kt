package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in diagnostics preferences. Both default **off** — nothing is sent until the
 * user explicitly opts in (on the first-run consent screen or in settings), and
 * only when the corresponding build key is configured.
 */
@Singleton
class AnalyticsPreferences @Inject constructor(store: PreferenceStore) {

    /** Send anonymous crash reports (GlitchTip). */
    val crashReportsEnabled = store.getBoolean("diagnostics_crash_reports", false)

    /** Send anonymous usage analytics (Aptabase). */
    val usageAnalyticsEnabled = store.getBoolean("diagnostics_usage_analytics", false)

    /** Whether the first-run consent prompt has been shown and answered. */
    val prompted = store.getBoolean("diagnostics_prompted", false)
}
