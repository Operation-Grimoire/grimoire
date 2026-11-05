package io.grimoire.app.data.backup

private val deviceOnlyPreferences = setOf(
    "tts_elevenlabs_api_key",
    "hidden_pin_hash",
    "hidden_pin_salt",
    "hidden_biometric_enabled",
)

// Source settings have no persisted password metadata. Exclude the entire namespace,
// including settings for extensions that are currently uninstalled.
internal fun shouldExportPreference(key: String): Boolean =
    !key.startsWith("source.") && key !in deviceOnlyPreferences
