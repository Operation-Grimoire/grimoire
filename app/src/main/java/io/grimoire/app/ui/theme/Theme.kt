package io.grimoire.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.grimoire.ui.theme.ColorTheme
import io.grimoire.ui.theme.GrimoireMaterialTheme
import io.grimoire.ui.theme.ThemeMode
import io.grimoire.ui.theme.isDark
import io.grimoire.ui.theme.scheme

@Composable
fun GrimoireTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorTheme.scheme(darkTheme)
    }

    // Match status/nav bar icon contrast to the app theme, not the system theme,
    // so an opposite-of-system theme doesn't render invisible bar icons.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    GrimoireMaterialTheme(colorScheme, content)
}
