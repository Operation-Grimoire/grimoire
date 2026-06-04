package io.grimoire.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
    /** Shown instead of [icon] when the tab has something to surface. */
    val activeIcon: ImageVector = icon,
) {
    Library("library", Icons.Default.LocalLibrary, "Library"),
    Browse("browse", Icons.Default.Explore, "Browse"),
    More("more", Icons.Default.MoreHoriz, "More", activeIcon = Icons.Default.NewReleases),
}

// Subpages nested under a top-level graph that should still hide the bottom navbar.
internal val routesWithoutBottomBar =
    setOf("nu_browser?mode={mode}", "nu_search", "nu_series?slug={slug}")

internal const val ROUTE_BROWSE_HOME = "browse_home"
internal const val ROUTE_GLOBAL_SEARCH = "global_search"
internal const val ROUTE_GLOBAL_SEARCH_ARG = "global_search?q={q}"
internal const val ROUTE_NU_BROWSER = "nu_browser?mode={mode}"
internal const val ROUTE_NU_SEARCH = "nu_search"
internal const val ROUTE_NU_SERIES = "nu_series?slug={slug}"
internal const val ROUTE_EXTENSION_MANAGE = "extensions"
internal const val ROUTE_SOURCE_BROWSE = "browse/{pkg}?q={q}"
internal const val ROUTE_SOURCE_SETTINGS = "settings/source/{pkg}"
internal const val ROUTE_SOURCE_LANGUAGES = "settings/source/{pkg}/languages"
internal const val ROUTE_SOURCE_LOGIN = "login/{pkg}"
internal const val ROUTE_NOVEL_DETAIL = "novel?pkg={pkg}&url={url}&migrateFrom={migrateFrom}"
internal const val ROUTE_MIGRATE = "migrate?novelId={novelId}"
internal const val ROUTE_DOWNLOADS = "downloads"
internal const val ROUTE_STATISTICS = "statistics"
internal const val ROUTE_TASKS = "tasks"
internal const val ROUTE_UPDATES = "updates"
internal const val ROUTE_UPDATE_ISSUES = "update_issues"
internal const val ROUTE_SETTINGS_ROOT = "settings"
internal const val ROUTE_SETTINGS_LIBRARY_UPDATE = "settings/library_updates"
internal const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
internal const val ROUTE_SETTINGS_BEHAVIOR = "settings/behavior"
internal const val ROUTE_SETTINGS_LIBRARY = "settings/library"
internal const val ROUTE_SETTINGS_BROWSE = "settings/browse"
internal const val ROUTE_SETTINGS_LANGUAGES = "settings/languages"
internal const val ROUTE_SETTINGS_CONTENT_LANGUAGES = "settings/languages/content"
internal const val ROUTE_SETTINGS_READER = "settings/reader"
internal const val ROUTE_SETTINGS_TTS = "settings/tts"
internal const val ROUTE_SETTINGS_TTS_VOICE = "settings/tts/voice?lang={lang}"
internal const val ROUTE_ABOUT = "about"
internal const val ROUTE_SETTINGS_BACKUP = "settings/backup"
internal const val ROUTE_SETTINGS_DATA = "settings/data"
internal const val ROUTE_SETTINGS_HIDDEN = "settings/hidden_categories"
internal const val ROUTE_SETTINGS_NOVELUPDATES = "settings/novelupdates"
internal const val ROUTE_SETTINGS_CONNECTIONS = "settings/connections"
internal const val ROUTE_SETTINGS_GITHUB = "settings/connections/github"
internal const val ROUTE_READER = "reader?pkg={pkg}&novelUrl={novelUrl}&chapterUrl={chapterUrl}"
internal const val ROUTE_WEBVIEW = "webview?url={url}"

internal const val POP_MS = 120

/** Stable name for an externally-requested destination (e.g. from a notification tap). */
const val NAV_TARGET_UPDATES = "updates"

internal const val SETTINGS_GRAPH_ROUTE = "settings_graph"
