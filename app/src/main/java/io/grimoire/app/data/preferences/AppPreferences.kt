package io.grimoire.app.data.preferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
)
