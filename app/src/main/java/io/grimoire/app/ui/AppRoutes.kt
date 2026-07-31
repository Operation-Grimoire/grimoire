package io.grimoire.app.ui

import io.grimoire.app.ui.icon.*
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import io.grimoire.app.R

internal enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    /** Shown instead of [icon] when the tab has something to surface. */
    val activeIcon: ImageVector = icon,
    /** Filled variant shown while the tab is selected. */
    val selectedIcon: ImageVector = icon,
    /** Filled [activeIcon] shown while the tab is selected and has something to surface. */
    val activeSelectedIcon: ImageVector = activeIcon,
) {
    // Declaration order is the bottom-nav order: Library holds the centre.
    More("more", AppIcons.MoreHoriz, R.string.nav_more, activeIcon = AppIcons.NewReleases, activeSelectedIcon = AppIcons.NewReleasesFilled),
    Library("library", AppIcons.LocalLibrary, R.string.nav_library, selectedIcon = AppIcons.LocalLibraryFilled),
    Browse("browse", AppIcons.Explore, R.string.nav_browse, selectedIcon = AppIcons.ExploreFilled),
}

// Subpages nested under a top-level graph that should still hide the bottom navbar.
internal val routesWithoutBottomBar = setOf(
    "nu_browser?mode={mode}",
    "nu_search",
    "nu_series?slug={slug}",
    "nu_bookmarks",
    "global_search",
    "global_search?q={q}",
)

internal const val ROUTE_BROWSE_HOME = "browse_home"
internal const val ROUTE_GLOBAL_SEARCH = "global_search"
internal const val ROUTE_GLOBAL_SEARCH_ARG = "global_search?q={q}"
internal const val ROUTE_NU_BROWSER = "nu_browser?mode={mode}"
internal const val ROUTE_NU_SEARCH = "nu_search"
internal const val ROUTE_NU_SERIES = "nu_series?slug={slug}"
internal const val ROUTE_NU_BOOKMARKS = "nu_bookmarks"
internal const val ROUTE_EXTENSION_MANAGE = "extensions?query={query}"

/**
 * Navigable form of [ROUTE_EXTENSION_MANAGE]. The constant above is the route
 * *pattern* — navigating to it verbatim delivers the literal text "{query}" as
 * the search prefill (the broken-tour bug, #316). Every programmatic
 * navigation goes through this builder instead.
 */
internal fun extensionManageRoute(query: String = ""): String =
    "extensions?query=${android.net.Uri.encode(query)}"
internal const val ROUTE_SOURCE_BROWSE = "browse/{pkg}?q={q}"
internal const val ROUTE_SOURCE_SETTINGS = "settings/source/{pkg}"
internal const val ROUTE_SOURCE_LANGUAGES = "settings/source/{pkg}/languages"
internal const val ROUTE_SOURCE_LOGIN = "login/{pkg}"
internal const val ROUTE_NOVEL_DETAIL = "novel?pkg={pkg}&url={url}&migrateFrom={migrateFrom}&sourceId={sourceId}"
internal const val ROUTE_MIGRATE = "migrate?novelId={novelId}"
internal const val ROUTE_DOWNLOADS = "downloads"
internal const val ROUTE_STATISTICS = "statistics"
internal const val ROUTE_TASKS = "tasks"
internal const val ROUTE_UPDATES = "updates"
internal const val ROUTE_HISTORY = "history"
internal const val ROUTE_UPDATE_ISSUES = "update_issues"
internal const val ROUTE_CRASH_REPORT = "crash_report"
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
internal const val ROUTE_TOURS = "tours"
internal const val ROUTE_SETTINGS_BACKUP = "settings/backup"
internal const val ROUTE_SETTINGS_DATA = "settings/data"
internal const val ROUTE_SETTINGS_PRIVACY = "settings/privacy"
internal const val ROUTE_SETTINGS_HIDDEN = "settings/hidden_categories"
internal const val ROUTE_SETTINGS_NOVELUPDATES = "settings/novelupdates"
internal const val ROUTE_SETTINGS_CONNECTIONS = "settings/connections"
internal const val ROUTE_SETTINGS_GITHUB = "settings/connections/github"
internal const val ROUTE_READER = "reader?pkg={pkg}&novelUrl={novelUrl}&chapterUrl={chapterUrl}"
internal const val ROUTE_WEBVIEW = "webview?url={url}"

/** Incognito banner open/close duration. */
internal const val BANNER_MS = 220

/**
 * Routes where the incognito banner is shown: the top-level tabs plus the discovery surfaces
 * where history is actually being recorded (Library, Browse + its sub-screens, More, and
 * Novel detail). Deep pages like the reader, settings, and the More sub-tools deliberately
 * don't carry it.
 */
internal val routesWithIncognitoBanner = setOf(
    TopLevelDestination.Library.route,
    TopLevelDestination.More.route,
    ROUTE_BROWSE_HOME,
    ROUTE_GLOBAL_SEARCH,
    ROUTE_GLOBAL_SEARCH_ARG,
    ROUTE_NU_BROWSER,
    ROUTE_NU_SEARCH,
    ROUTE_NU_SERIES,
    ROUTE_NU_BOOKMARKS,
    ROUTE_SOURCE_BROWSE,
    ROUTE_NOVEL_DETAIL,
)

/** Stable name for an externally-requested destination (e.g. from a notification tap). */
const val NAV_TARGET_UPDATES = "updates"

/** Stable name for the crash-report screen, surfaced on the first launch after a crash. */
const val NAV_TARGET_CRASH = "crash"

internal const val SETTINGS_GRAPH_ROUTE = "settings_graph"
