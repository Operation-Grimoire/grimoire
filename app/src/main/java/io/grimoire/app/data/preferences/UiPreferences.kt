package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class UiPreferences @Inject constructor(store: PreferenceStore) {
    val themeMode = store.getEnum("theme_mode", ThemeMode.SYSTEM)
    val useDynamicColor = store.getBoolean("use_dynamic_color", true)
}
