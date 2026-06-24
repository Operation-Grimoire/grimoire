package io.grimoire.app.data.preferences

import io.grimoire.app.data.schedule.ScheduleUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryUpdatePreferences @Inject constructor(store: PreferenceStore) {
    /** Whether the periodic library refresh is scheduled at all. */
    val enabled = store.getBoolean("library_update_enabled", false)
    /** Multiplier on [intervalUnit]; e.g. count 3 + HOURS = every 3 hours. */
    val intervalCount = store.getInt("library_update_interval_count", 1)
    val intervalUnit = store.getEnum("library_update_interval_unit", ScheduleUnit.DAYS)
    val onlyOnWifi = store.getBoolean("library_update_only_on_wifi", false)
    val requiresCharging = store.getBoolean("library_update_requires_charging", false)
    val concurrency = store.getInt("library_update_concurrency", 4)
    /**
     * When false (default), EPUB-typed novels are skipped by library sync: local
     * file imports and EPUB-source extensions (e.g. Z-Library, libgen) have no
     * scraped chapter list to refresh, so there's nothing to fetch. Enable to pull
     * them into the run anyway.
     */
    val includeEpubsInSync = store.getBoolean("library_update_include_epubs", false)
    /** Preferred minutes-since-midnight for the scheduled run; default 03:00. */
    val preferredTimeOfDayMinutes = store.getInt("library_update_preferred_time_minutes", 180)
    val lastRunAt = store.getString("library_update_last_run_at", "0")
    val lastRunSuccess = store.getBoolean("library_update_last_run_success", true)
    val lastRunMessage = store.getString("library_update_last_run_message", "")
}
