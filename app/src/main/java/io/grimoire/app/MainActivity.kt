package io.grimoire.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import io.grimoire.app.ui.PendingAddRepo
import io.grimoire.app.ui.parseAddRepoLink
import io.grimoire.app.ui.component.ProvideAppHaptics
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

    /** EPUB URI from an external "Open with" intent; cleared once the import flow picks it up. */
    private val pendingEpubUri = MutableStateFlow<Uri?>(null)

    /** Inbound "add this extension repo" deep link; cleared once Extensions picks it up. */
    private val pendingAddRepo = MutableStateFlow<PendingAddRepo?>(null)

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
        // Order matters: consumeAddRepoLink only clears intent.data when the
        // URI matches the add-repo scheme/host, so on an EPUB intent it's a
        // no-op and consumeEpubUri still sees the URI. consumeEpubUri,
        // however, eats any VIEW URI — so it must run last.
        pendingAddRepo.value = consumeAddRepoLink(intent)
        pendingEpubUri.value = consumeEpubUri(intent)
        setContent {
            val themeMode by uiPreferences.themeMode.changes()
                .collectAsState(initial = uiPreferences.themeMode.defaultValue())
            val dynamicColor by uiPreferences.useDynamicColor.changes()
                .collectAsState(initial = uiPreferences.useDynamicColor.defaultValue())
            val colorTheme by uiPreferences.colorTheme.changes()
                .collectAsState(initial = uiPreferences.colorTheme.defaultValue())
            val hapticsEnabled by uiPreferences.hapticsEnabled.changes()
                .collectAsState(initial = uiPreferences.hapticsEnabled.defaultValue())
            GrimoireTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorTheme = colorTheme,
            ) {
                ProvideAppHaptics(enabled = hapticsEnabled) {
                    AppNavigation(
                        pendingTarget = pendingTarget.asStateFlow(),
                        onTargetHandled = { pendingTarget.value = null },
                        pendingEpubUri = pendingEpubUri.asStateFlow(),
                        onEpubUriHandled = { pendingEpubUri.value = null },
                        pendingAddRepo = pendingAddRepo.asStateFlow(),
                        onAddRepoHandled = { pendingAddRepo.value = null },
                    )
                    AppUpdateUi()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavTarget(intent)?.let { pendingTarget.value = it }
        consumeAddRepoLink(intent)?.let { pendingAddRepo.value = it }
        consumeEpubUri(intent)?.let { pendingEpubUri.value = it }
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

    /**
     * Extracts an EPUB URI from a VIEW intent and clears it from the activity intent
     * so a config-change rebuild doesn't re-trigger the import dialog on every rotation.
     */
    private fun consumeEpubUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        intent.data = null
        return uri
    }

    /**
     * Parses an inbound add-repo deep link (https://grimoireapp.org/add-repo
     * or grimoire://add-repo) and clears the intent so a rotation rebuild
     * doesn't reopen the dialog.
     */
    private fun consumeAddRepoLink(intent: Intent?): PendingAddRepo? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val parsed = parseAddRepoLink(
            scheme = uri.scheme,
            host = uri.host,
            path = uri.path,
            urlParam = uri.getQueryParameter("url"),
            nameParam = uri.getQueryParameter("name"),
        ) ?: return null
        intent.data = null
        return parsed
    }

    companion object {
        /** Intent extra carrying a [io.grimoire.app.ui.NAV_TARGET_*] value to route to on launch. */
        const val EXTRA_NAV_TARGET = "io.grimoire.app.NAV_TARGET"
    }
}
