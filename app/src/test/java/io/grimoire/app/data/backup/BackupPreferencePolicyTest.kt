package io.grimoire.app.data.backup

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferencePolicyTest {
    @Test fun `credentials never travel including unknown extension settings`() {
        listOf("tts_elevenlabs_api_key", "hidden_pin_hash", "hidden_pin_salt",
            "hidden_biometric_enabled", "source.uninstalled.extension.password",
            "source.unknown.future_setting").forEach { assertFalse(it, shouldExportPreference(it)) }
    }

    @Test fun `portable preferences remain available`() {
        listOf("reader_font_size", "library_sort", "include_hidden_in_all").forEach {
            assertTrue(it, shouldExportPreference(it))
        }
    }

    @Test fun `older backups keep their credential entries for restore`() {
        val backup = Json.decodeFromString<BackupFile>("""{
            "version": 2, "createdAt": 1,
            "preferences": [
                {"key":"source.old.extension.password","type":"s","value":"test-password"},
                {"key":"hidden_pin_hash","type":"s","value":"test-hash"},
                {"key":"hidden_pin_salt","type":"s","value":"test-salt"}
            ]
        }""")
        val restored = mutablePreferencesOf()
        restoreBackupPreferences(restored, backup.preferences)
        assertEquals("test-password", restored[stringPreferencesKey("source.old.extension.password")])
        assertEquals("test-hash", restored[stringPreferencesKey("hidden_pin_hash")])
        assertEquals("test-salt", restored[stringPreferencesKey("hidden_pin_salt")])
    }

}
