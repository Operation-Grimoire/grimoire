package io.grimoire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.ui.AppNavigation
import io.grimoire.app.ui.theme.GrimoireTheme
import io.grimoire.app.ui.update.AppUpdateUi
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uiPreferences: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by uiPreferences.themeMode.changes()
                .collectAsState(initial = uiPreferences.themeMode.defaultValue())
            val dynamicColor by uiPreferences.useDynamicColor.changes()
                .collectAsState(initial = uiPreferences.useDynamicColor.defaultValue())
            GrimoireTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                AppNavigation()
                AppUpdateUi()
            }
        }
    }
}
