package io.grimoire.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.ui.AppNavigation
import io.grimoire.app.ui.theme.GrimoireTheme
import io.grimoire.app.ui.update.AppUpdateUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var uiPreferences: UiPreferences

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Destination requested by an inbound intent (e.g. a notification tap); cleared once consumed. */
    private val pendingTarget = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        pendingTarget.value = consumeNavTarget(intent)
        setContent {
            val themeMode by uiPreferences.themeMode.changes()
                .collectAsState(initial = uiPreferences.themeMode.defaultValue())
            val dynamicColor by uiPreferences.useDynamicColor.changes()
                .collectAsState(initial = uiPreferences.useDynamicColor.defaultValue())
            val colorTheme by uiPreferences.colorTheme.changes()
                .collectAsState(initial = uiPreferences.colorTheme.defaultValue())
            GrimoireTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorTheme = colorTheme,
            ) {
                AppNavigation(
                    pendingTarget = pendingTarget.asStateFlow(),
                    onTargetHandled = { pendingTarget.value = null },
                )
                AppUpdateUi()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavTarget(intent)?.let { pendingTarget.value = it }
    }

    /**
     * Reads a nav-target extra and removes it from the intent so a config-change
     * rebuild doesn't re-navigate the user to the same target on every rotation.
     */
    private fun consumeNavTarget(intent: Intent?): String? {
        if (intent == null) return null
        val target = intent.getStringExtra(EXTRA_NAV_TARGET) ?: return null
        intent.removeExtra(EXTRA_NAV_TARGET)
        return target
    }

    companion object {
        /** Intent extra carrying a [io.grimoire.app.ui.NAV_TARGET_*] value to route to on launch. */
        const val EXTRA_NAV_TARGET = "io.grimoire.app.NAV_TARGET"
    }
}
