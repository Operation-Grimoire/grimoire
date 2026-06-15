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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.ui.AppNavigation
import io.grimoire.app.ui.PendingAddRepo
import io.grimoire.app.ui.isAddRepoLink
import io.grimoire.app.ui.parseAddRepoLink
import io.grimoire.app.ui.component.LocalSynopsisRenderLinks
import io.grimoire.app.ui.component.ProvideAppHaptics
import io.grimoire.app.ui.theme.GrimoireTheme
import io.grimoire.app.ui.update.AppUpdateUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        pendingAddRepo.value = consumeAddRepoLink(intent)
        pendingEpubUri.value = consumeEpubUri(intent)
        // Await the persisted theme before composing anything: seeding collectAsState
        // with defaults paints the first frame in the default theme and then snaps to
        // the user's, a visible flash on every cold start for non-default themes. The
        // window background covers the (few-ms) DataStore read, like a splash would.
        val themeState = uiPreferences.themeState()
        lifecycleScope.launch {
            val initialTheme = themeState.first()
            setContent {
                val theme by themeState.collectAsState(initial = initialTheme)
                GrimoireTheme(
                    themeMode = theme.themeMode,
                    dynamicColor = theme.useDynamicColor,
                    colorTheme = theme.colorTheme,
                ) {
                    CompositionLocalProvider(
                        LocalSynopsisRenderLinks provides theme.renderSynopsisLinks,
                    ) {
                        ProvideAppHaptics(enabled = theme.hapticsEnabled) {
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
     * Extracts a content:// or file:// URI from a VIEW intent (the schemes our
     * EPUB intent-filter declares) and clears it from the activity intent so a
     * config-change rebuild doesn't re-trigger the import dialog on every
     * rotation. Other schemes (e.g. `grimoire://`) are ignored so they don't
     * get force-fed to the EPUB importer.
     */
    private fun consumeEpubUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "content" && uri.scheme != "file") return null
        intent.data = null
        return uri
    }

    /**
     * Parses an inbound add-repo deep link (https://grimoireapp.org/add-repo
     * or grimoire://add-repo). Any URI matching the scheme/host is consumed
     * (intent.data cleared) even when params don't parse, so a malformed link
     * silently no-ops instead of leaking through to EPUB import.
     */
    private fun consumeAddRepoLink(intent: Intent?): PendingAddRepo? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (!isAddRepoLink(uri.scheme, uri.host, uri.path)) return null
        intent.data = null
        return parseAddRepoLink(
            scheme = uri.scheme,
            host = uri.host,
            path = uri.path,
            urlParam = uri.getQueryParameter("url"),
            nameParam = uri.getQueryParameter("name"),
        )
    }

    companion object {
        /** Intent extra carrying a [io.grimoire.app.ui.NAV_TARGET_*] value to route to on launch. */
        const val EXTRA_NAV_TARGET = "io.grimoire.app.NAV_TARGET"
    }
}
