package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class BackupFrequency(val hours: Long) {
    OFF(0),
    DAILY(24),
    EVERY_3_DAYS(72),
    WEEKLY(168),
}

@Singleton
class BackupPreferences @Inject constructor(store: PreferenceStore) {
    val backupFolderUri = store.getString("backup_folder_uri", "")
    val frequency = store.getEnum("backup_frequency", BackupFrequency.OFF)
    val lastAutoBackupAt = store.getString("backup_last_auto_at", "0")
    val lastAutoBackupFile = store.getString("backup_last_auto_file", "")
    val lastAutoBackupSuccess = store.getBoolean("backup_last_auto_success", true)
    val lastAutoBackupMessage = store.getString("backup_last_auto_message", "")
    val onlyOnWifi = store.getBoolean("backup_only_on_wifi", false)
    val requiresCharging = store.getBoolean("backup_requires_charging", false)
}
