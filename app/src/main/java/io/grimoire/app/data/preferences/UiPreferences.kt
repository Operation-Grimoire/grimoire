package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorTheme(val displayName: String) {
    DEFAULT("Default"),
    GRIMOIRE("Grimoire"),
    OCEAN("Ocean"),
    SUNSET("Sunset"),
    FOREST("Forest"),
    ROSE("Rose"),
    MIDNIGHT("Midnight"),
}

@Singleton
class UiPreferences @Inject constructor(store: PreferenceStore) {
    val themeMode = store.getEnum("theme_mode", ThemeMode.SYSTEM)
    val useDynamicColor = store.getBoolean("use_dynamic_color", true)
    val colorTheme = store.getEnum("color_theme", ColorTheme.DEFAULT)
    val hapticsEnabled = store.getBoolean("haptics_enabled", true)
}
