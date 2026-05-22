package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryUpdateFrequency(val hours: Long) {
    OFF(0),
    DAILY(24),
    EVERY_3_DAYS(72),
    WEEKLY(168),
}

@Singleton
class LibraryUpdatePreferences @Inject constructor(store: PreferenceStore) {
    val frequency = store.getEnum("library_update_frequency", LibraryUpdateFrequency.OFF)
    val onlyOnWifi = store.getBoolean("library_update_only_on_wifi", false)
    val requiresCharging = store.getBoolean("library_update_requires_charging", false)
    val lastRunAt = store.getString("library_update_last_run_at", "0")
    val lastRunSuccess = store.getBoolean("library_update_last_run_success", true)
    val lastRunMessage = store.getString("library_update_last_run_message", "")
}
