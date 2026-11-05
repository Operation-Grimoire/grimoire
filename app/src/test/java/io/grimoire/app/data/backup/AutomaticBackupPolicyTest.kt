package io.grimoire.app.data.backup

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBackupPolicyTest {
    private fun file(day: Int) = AutomaticBackupFile(
        "uri:$day",
        "grimoire_auto_backup_2026-11-${day.toString().padStart(2, '0')}_010203_004_" +
            "00000000-0000-0000-0000-000000000000.json.gz",
    )

    @Test fun `revoked access never retries and transient failures get three total attempts`() {
        (0..10).forEach { assertFalse(shouldRetryBackup(false, it)) }
        assertTrue(shouldRetryBackup(true, 0))
        assertTrue(shouldRetryBackup(true, 1))
        (2..10).forEach { assertFalse(shouldRetryBackup(true, it)) }
    }

    @Test fun `keep seven successful automatic backups and the current backup`() {
        val files = (1..10).map(::file)
        val tracked = files.map { it.uri }.toSet()
        assertEquals(listOf("uri:3", "uri:2", "uri:1"), automaticBackupsToDelete(files, tracked, "uri:10"))
        // A clock correction must not prune the backup that just succeeded.
        assertEquals(listOf("uri:4", "uri:3", "uri:2"), automaticBackupsToDelete(files, tracked, "uri:1"))
    }

    @Test fun `names alone never prove ownership and manual or renamed files never qualify`() {
        val files = (1..10).map(::file)
        val manual = AutomaticBackupFile("manual", "grimoire_backup_2026-11-01_010203.json.gz")
        val renamed = AutomaticBackupFile("renamed", "other.json.gz")
        val tracked = files.map { it.uri }.toSet() + manual.uri + renamed.uri
        val untracked = file(11)
        assertEquals(listOf("uri:3", "uri:2", "uri:1"), automaticBackupsToDelete(
            files + manual + renamed + untracked, tracked, "uri:10",
        ))
    }

    @Test fun `missing latest file or a single good backup never permits deletion`() {
        val files = (1..10).map(::file)
        assertTrue(automaticBackupsToDelete(files, files.map { it.uri }.toSet(), "missing").isEmpty())
        assertTrue(automaticBackupsToDelete(listOf(file(1)), setOf("uri:1"), "uri:1").isEmpty())
    }

    @Test fun `restore cannot import deletion authority from a backup`() {
        val key = AUTOMATIC_BACKUP_REGISTRY_PREFIX + "folder"
        assertFalse(shouldExportPreference(key))
        val prefs = mutablePreferencesOf()
        restoreBackupPreferences(prefs, listOf(BackupPreference(key, "ss", stringSet = listOf("uri:1"))))
        assertNull(prefs[stringSetPreferencesKey(key)])
    }
}
