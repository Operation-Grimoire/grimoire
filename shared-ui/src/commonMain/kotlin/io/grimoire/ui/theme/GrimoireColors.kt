package io.grimoire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Persisted by name on both platforms; keep existing constant names. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted by name on both platforms; keep existing constant names. */
enum class ColorTheme(val displayName: String) {
    DEFAULT("Default"),
    GRIMOIRE("Grimoire"),
    OCEAN("Ocean"),
    SUNSET("Sunset"),
    FOREST("Forest"),
    ROSE("Rose"),
    MIDNIGHT("Midnight"),
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun ColorTheme.scheme(dark: Boolean): ColorScheme = when (this) {
    ColorTheme.DEFAULT -> if (dark) DefaultDark else DefaultLight
    ColorTheme.GRIMOIRE -> if (dark) GrimoireDark else GrimoireLight
    ColorTheme.OCEAN -> if (dark) OceanDark else OceanLight
    ColorTheme.SUNSET -> if (dark) SunsetDark else SunsetLight
    ColorTheme.FOREST -> if (dark) ForestDark else ForestLight
    ColorTheme.ROSE -> if (dark) RoseDark else RoseLight
    ColorTheme.MIDNIGHT -> if (dark) MidnightDark else MidnightLight
}

/** Material theme with Grimoire typography; each platform chooses the color scheme. */
@Composable
fun GrimoireMaterialTheme(colorScheme: ColorScheme, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
