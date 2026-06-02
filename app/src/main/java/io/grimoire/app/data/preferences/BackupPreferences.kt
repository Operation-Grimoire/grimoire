package io.grimoire.app.data.preferences

import io.grimoire.app.data.schedule.ScheduleUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupPreferences @Inject constructor(store: PreferenceStore) {
    val backupFolderUri = store.getString("backup_folder_uri", "")
    /** Whether the periodic backup is scheduled at all. */
    val enabled = store.getBoolean("backup_enabled", false)
    /** Multiplier on [intervalUnit]; e.g. count 3 + DAYS = every 3 days. */
    val intervalCount = store.getInt("backup_interval_count", 1)
    val intervalUnit = store.getEnum("backup_interval_unit", ScheduleUnit.DAYS)
    /** Preferred minutes-since-midnight for the scheduled backup; default 03:00. */
    val preferredTimeOfDayMinutes = store.getInt("backup_preferred_time_minutes", 180)
    val lastAutoBackupAt = store.getString("backup_last_auto_at", "0")
    val lastAutoBackupFile = store.getString("backup_last_auto_file", "")
    val lastAutoBackupSuccess = store.getBoolean("backup_last_auto_success", true)
    val lastAutoBackupMessage = store.getString("backup_last_auto_message", "")
    val onlyOnWifi = store.getBoolean("backup_only_on_wifi", false)
    val requiresCharging = store.getBoolean("backup_requires_charging", false)
}
