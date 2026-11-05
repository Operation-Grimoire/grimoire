package io.grimoire.app.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPreferenceRestoreTest {
    @Test fun `wrong types cannot poison current readers even on a fresh device`() {
        val prefs = mutablePreferencesOf()
        restoreBackupPreferences(prefs, listOf(
            BackupPreference("reader_font_size", "s", "old-string"),
            BackupPreference("source.uninstalled.content_languages_override", "s", "true"),
            BackupPreference("tour_completed_reader", "b", "true"),
            BackupPreference("theme_mode", "s", "DARK"),
        ))
        assertNull(prefs[intPreferencesKey("reader_font_size")])
        assertNull(prefs[booleanPreferencesKey("source.uninstalled.content_languages_override")])
        assertNull(prefs[intPreferencesKey("tour_completed_reader")])
        assertEquals("DARK", prefs[stringPreferencesKey("theme_mode")])
    }

    @Test fun `bad entries are skipped independently and valid legacy credentials still restore`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("reader_font_size") to 20,
            booleanPreferencesKey("extension_private_flag") to false)
        restoreBackupPreferences(prefs, listOf(
            BackupPreference("reader_font_size", "s", "bad"),
            BackupPreference("extension_private_flag", "s", "bad"),
            BackupPreference("download_concurrency", "i", "not-a-number"),
            BackupPreference("hidden_pin_hash", "s", "test-hash"),
            BackupPreference("source.uninstalled.password", "s", "test-password"),
            BackupPreference("reader_paragraph_spacing", "i", "24"),
        ))
        assertEquals(20, prefs[intPreferencesKey("reader_font_size")])
        assertFalse(prefs[booleanPreferencesKey("extension_private_flag")]!!)
        assertNull(prefs[intPreferencesKey("download_concurrency")])
        assertEquals("test-hash", prefs[stringPreferencesKey("hidden_pin_hash")])
        assertEquals("test-password", prefs[stringPreferencesKey("source.uninstalled.password")])
        assertEquals(24, prefs[intPreferencesKey("reader_paragraph_spacing")])
    }
}
