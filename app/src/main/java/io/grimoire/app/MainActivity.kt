package io.grimoire.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.ui.AppNavigation
import io.grimoire.app.ui.theme.GrimoireTheme
import io.grimoire.app.ui.update.AppUpdateUi
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uiPreferences: UiPreferences

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
