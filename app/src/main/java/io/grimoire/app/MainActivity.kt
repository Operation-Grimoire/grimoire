package io.grimoire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.ui.AppNavigation
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.theme.GrimoireTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by settingsViewModel.preferences.collectAsState()
            GrimoireTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.useDynamicColor,
            ) {
                AppNavigation()
            }
        }
    }
}
