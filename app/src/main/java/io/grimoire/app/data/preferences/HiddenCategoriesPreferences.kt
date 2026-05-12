package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenCategoriesPreferences @Inject constructor(store: PreferenceStore) {
    val pinHash = store.getString("hidden_pin_hash", "")
    val pinSalt = store.getString("hidden_pin_salt", "")
    val biometricEnabled = store.getBoolean("hidden_biometric_enabled", false)
}
