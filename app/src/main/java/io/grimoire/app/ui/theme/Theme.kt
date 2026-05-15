package io.grimoire.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.grimoire.app.data.preferences.ColorTheme
import io.grimoire.app.data.preferences.ThemeMode

@Composable
fun GrimoireTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorTheme.scheme(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private fun ColorTheme.scheme(dark: Boolean): ColorScheme = when (this) {
    ColorTheme.DEFAULT -> if (dark) DefaultDark else DefaultLight
    ColorTheme.GRIMOIRE -> if (dark) GrimoireDark else GrimoireLight
    ColorTheme.OCEAN -> if (dark) OceanDark else OceanLight
    ColorTheme.SUNSET -> if (dark) SunsetDark else SunsetLight
    ColorTheme.FOREST -> if (dark) ForestDark else ForestLight
    ColorTheme.ROSE -> if (dark) RoseDark else RoseLight
    ColorTheme.MIDNIGHT -> if (dark) MidnightDark else MidnightLight
}
