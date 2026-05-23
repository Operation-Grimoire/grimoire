package io.grimoire.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.grimoire.app.ui.screen.browse.BrowseScreen
import io.grimoire.app.ui.screen.browse.BrowseViewModel
import io.grimoire.app.ui.screen.browse.GlobalSearchScreen
import io.grimoire.app.ui.screen.browse.NovelDetailScreen
import io.grimoire.app.ui.screen.browse.SourceBrowseScreen
import io.grimoire.app.ui.screen.downloads.DownloadsScreen
import io.grimoire.app.ui.screen.extensions.ExtensionsScreen
import io.grimoire.app.ui.screen.library.LibraryScreen
import io.grimoire.app.ui.screen.migrate.MigrateScreen
import io.grimoire.app.ui.screen.more.MoreScreen
import io.grimoire.app.ui.screen.more.MoreViewModel
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesBrowserScreen
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesSearchScreen
import io.grimoire.app.ui.screen.novelupdates.NovelUpdatesSeriesScreen
import io.grimoire.app.ui.screen.more.statistics.StatisticsScreen
import io.grimoire.app.ui.screen.reader.ReaderScreen
import io.grimoire.app.ui.screen.settings.SettingsScreen
import io.grimoire.app.ui.screen.webview.SOURCE_LOGIN_RESULT_KEY
import io.grimoire.app.ui.screen.webview.SourceLoginScreen
import io.grimoire.app.ui.screen.webview.WebViewScreen
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.about.AboutSettingsScreen
import io.grimoire.app.ui.screen.settings.appearance.AppearanceSettingsScreen
import io.grimoire.app.ui.screen.settings.backup.BackupSettingsScreen
import io.grimoire.app.ui.screen.settings.behavior.BehaviorSettingsScreen
import io.grimoire.app.ui.screen.settings.libraryupdate.LibraryUpdateSettingsScreen
import io.grimoire.app.ui.screen.tasks.TasksScreen
import io.grimoire.app.ui.screen.updates.LibraryUpdatesScreen
import io.grimoire.app.ui.screen.updates.UpdateIssuesScreen
import io.grimoire.app.ui.screen.settings.browse.BrowseLanguagesScreen
import io.grimoire.app.ui.screen.settings.browse.BrowseSettingsScreen
import io.grimoire.app.ui.screen.settings.hidden.HiddenCategoriesSettingsScreen
import io.grimoire.app.ui.screen.settings.languages.LanguagesSettingsScreen
import io.grimoire.app.ui.screen.settings.library.LibrarySettingsScreen
import io.grimoire.app.ui.screen.settings.novelupdates.NovelUpdatesSettingsScreen
import io.grimoire.app.ui.screen.settings.reader.ReaderSettingsScreen
import io.grimoire.app.ui.screen.settings.source.SourceLanguagesScreen
import io.grimoire.app.ui.screen.settings.source.SourceSettingsScreen
import io.grimoire.app.ui.screen.settings.tts.TtsSettingsScreen
import io.grimoire.app.ui.screen.settings.tts.TtsVoicePickerScreen

private enum class TopLevelDestination(
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

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

// Subpages nested under a top-level graph that should still hide the bottom navbar.
private val routesWithoutBottomBar =
    setOf("nu_browser?mode={mode}", "nu_search", "nu_series?slug={slug}")

private const val ROUTE_BROWSE_HOME = "browse_home"
private const val ROUTE_GLOBAL_SEARCH = "global_search"
private const val ROUTE_GLOBAL_SEARCH_ARG = "global_search?q={q}"
private const val ROUTE_NU_BROWSER = "nu_browser?mode={mode}"
private const val ROUTE_NU_SEARCH = "nu_search"
private const val ROUTE_NU_SERIES = "nu_series?slug={slug}"
private const val ROUTE_EXTENSION_MANAGE = "extensions"
private const val ROUTE_SOURCE_BROWSE = "browse/{pkg}?q={q}"
private const val ROUTE_SOURCE_SETTINGS = "settings/source/{pkg}"
private const val ROUTE_SOURCE_LANGUAGES = "settings/source/{pkg}/languages"
private const val ROUTE_SOURCE_LOGIN = "login/{pkg}"
private const val ROUTE_NOVEL_DETAIL = "novel?pkg={pkg}&url={url}&migrateFrom={migrateFrom}"
private const val ROUTE_MIGRATE = "migrate?novelId={novelId}"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_STATISTICS = "statistics"
private const val ROUTE_TASKS = "tasks"
private const val ROUTE_UPDATES = "updates"
private const val ROUTE_UPDATE_ISSUES = "update_issues"
private const val ROUTE_SETTINGS_ROOT = "settings"
private const val ROUTE_SETTINGS_LIBRARY_UPDATE = "settings/library_updates"
private const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
private const val ROUTE_SETTINGS_BEHAVIOR = "settings/behavior"
private const val ROUTE_SETTINGS_LIBRARY = "settings/library"
private const val ROUTE_SETTINGS_BROWSE = "settings/browse"
private const val ROUTE_SETTINGS_LANGUAGES = "settings/languages"
private const val ROUTE_SETTINGS_CONTENT_LANGUAGES = "settings/languages/content"
private const val ROUTE_SETTINGS_READER = "settings/reader"
private const val ROUTE_SETTINGS_TTS = "settings/tts"
private const val ROUTE_SETTINGS_TTS_VOICE = "settings/tts/voice?lang={lang}"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_SETTINGS_BACKUP = "settings/backup"
private const val ROUTE_SETTINGS_HIDDEN = "settings/hidden_categories"
private const val ROUTE_SETTINGS_NOVELUPDATES = "settings/novelupdates"
private const val ROUTE_READER = "reader?pkg={pkg}&novelUrl={novelUrl}&chapterUrl={chapterUrl}"
private const val ROUTE_WEBVIEW = "webview?url={url}"

private const val POP_MS = 120

/** Stable name for an externally-requested destination (e.g. from a notification tap). */
const val NAV_TARGET_UPDATES = "updates"

private val EmptyNavTarget: StateFlow<String?> = MutableStateFlow(null)

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    pendingTarget: StateFlow<String?> = EmptyNavTarget,
    onTargetHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val target by pendingTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        val route = when (t) {
            NAV_TARGET_UPDATES -> ROUTE_UPDATES
            else -> null
        }
        if (route != null) navController.navigate(route) { launchSingleTop = true }
        onTargetHandled()
    }

    val isTopLevel = TopLevelDestination.entries.any { dest ->
        backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
    } && currentRoute !in routesWithoutBottomBar

    // The Library hides the app nav while multi-selecting so its selection
    // action bar can take that space.
    var libraryInSelection by remember { mutableStateOf(false) }
    val hideNavForSelection = libraryInSelection &&
        currentRoute == TopLevelDestination.Library.route

    val moreVm: MoreViewModel = hiltViewModel()
    val activeDownloadCount by moreVm.activeDownloadCount.collectAsState()
    val updateCount by moreVm.updateCount.collectAsState()
    val extensionUpdateCount by moreVm.extensionUpdateCount.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isTopLevel) {
                AnimatedVisibility(
                    visible = !hideNavForSelection,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                NavigationBar {
                    val haptics = LocalHapticFeedback.current
                    TopLevelDestination.entries.forEach { dest ->
                        val isSelected = backStack?.destination?.hierarchy
                            ?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                val alreadyOnTab = isSelected
                                if (!alreadyOnTab) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                                if (alreadyOnTab && dest == TopLevelDestination.Browse) {
                                    navController.navigate(ROUTE_GLOBAL_SEARCH) {
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                val updateBadge = dest == TopLevelDestination.Browse &&
                                    extensionUpdateCount > 0
                                val downloadBadge = dest == TopLevelDestination.More &&
                                    activeDownloadCount > 0
                                // The More tab swaps to the Updates icon while the
                                // updates log has entries to surface.
                                val hasUpdates = dest == TopLevelDestination.More &&
                                    updateCount > 0
                                val tabIcon = if (hasUpdates) dest.activeIcon else dest.icon
                                when {
                                    updateBadge -> BadgedBox(
                                        badge = { Badge { Text("$extensionUpdateCount") } },
                                    ) {
                                        Icon(tabIcon, contentDescription = dest.label)
                                    }
                                    downloadBadge -> BadgedBox(badge = { Badge() }) {
                                        Icon(tabIcon, contentDescription = dest.label)
                                    }
                                    else -> Icon(tabIcon, contentDescription = dest.label)
                                }
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            modifier = Modifier.padding(padding),
            enterTransition = { scaleIn(tween(POP_MS), initialScale = 0.92f) + fadeIn(tween(POP_MS)) },
            exitTransition = { scaleOut(tween(POP_MS), targetScale = 1.08f) + fadeOut(tween(POP_MS)) },
            popEnterTransition = { scaleIn(tween(POP_MS), initialScale = 0.92f) + fadeIn(tween(POP_MS)) },
            popExitTransition = { scaleOut(tween(POP_MS), targetScale = 0.92f) + fadeOut(tween(POP_MS)) },
        ) {
            composable(route = TopLevelDestination.Library.route) {
                LibraryScreen(
                    onNovelClick = { pkg, url ->
                        navController.navigate(
                            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(url)}"
                        )
                    },
                    onBrowse = {
                        navController.navigate(TopLevelDestination.Browse.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSelectionActiveChange = { libraryInSelection = it },
                )
            }

            navigation(
                startDestination = ROUTE_BROWSE_HOME,
                route = TopLevelDestination.Browse.route,
            ) {
                composable(route = ROUTE_BROWSE_HOME) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry(TopLevelDestination.Browse.route) }
                    val vm: BrowseViewModel = hiltViewModel(graphEntry)
                    BrowseScreen(
                        onNavigateToManage = { navController.navigate(ROUTE_EXTENSION_MANAGE) },
                        onNavigateToSource = { pkg -> navController.navigate("browse/$pkg") },
                        onNavigateToGlobalSearch = { navController.navigate(ROUTE_GLOBAL_SEARCH) },
                        onNavigateToNovelUpdatesSearch = { navController.navigate(ROUTE_NU_SEARCH) },
                        onNavigateToNovelUpdatesRankings = {
                            navController.navigate("nu_browser?mode=RANKINGS")
                        },
                        onNavigateToNovelUpdatesLatest = {
                            navController.navigate("nu_browser?mode=LATEST")
                        },
                        viewModel = vm,
                    )
                }
                composable(
                    route = ROUTE_GLOBAL_SEARCH_ARG,
                    arguments = listOf(
                        navArgument("q") {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                    ),
                ) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry(TopLevelDestination.Browse.route) }
                    val vm: BrowseViewModel = hiltViewModel(graphEntry)
                    val q = entry.arguments?.getString("q")
                    LaunchedEffect(q) {
                        if (!q.isNullOrBlank()) {
                            vm.setQuery(q)
                            vm.submitSearch()
                        }
                    }
                    GlobalSearchScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNovelClick = { novel, pkg ->
                            navController.navigate(
                                "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novel.url)}"
                            )
                        },
                        onNavigateToSourceSearch = { pkg, query ->
                            navController.navigate("browse/$pkg?q=${Uri.encode(query)}")
                        },
                        viewModel = vm,
                    )
                }
                composable(
                    route = ROUTE_NU_BROWSER,
                    arguments = listOf(
                        navArgument("mode") {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                    ),
                ) {
                    NovelUpdatesBrowserScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onSeriesClick = { slug ->
                            navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                        },
                        onOpenWebView = { url ->
                            navController.navigate("webview?url=${Uri.encode(url)}")
                        },
                    )
                }
                composable(route = ROUTE_NU_SEARCH) {
                    NovelUpdatesSearchScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onSeriesClick = { slug ->
                            navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                        },
                        onOpenWebView = { url ->
                            navController.navigate("webview?url=${Uri.encode(url)}")
                        },
                    )
                }
                composable(
                    route = ROUTE_NU_SERIES,
                    arguments = listOf(navArgument("slug") { type = NavType.StringType }),
                ) {
                    NovelUpdatesSeriesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onFindInSources = { title ->
                            navController.navigate("global_search?q=${Uri.encode(title)}")
                        },
                        onOpenSeries = { slug ->
                            navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                        },
                        onOpenWebView = { url ->
                            navController.navigate("webview?url=${Uri.encode(url)}")
                        },
                    )
                }
            }

            composable(route = TopLevelDestination.More.route) {
                MoreScreen(
                    onNavigateToTasks = { navController.navigate(ROUTE_TASKS) },
                    onNavigateToUpdates = { navController.navigate(ROUTE_UPDATES) },
                    onNavigateToWarnings = { navController.navigate(ROUTE_UPDATE_ISSUES) },
                    onNavigateToDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                    onNavigateToStatistics = { navController.navigate(ROUTE_STATISTICS) },
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS_ROOT) },
                    onNavigateToAbout = { navController.navigate(ROUTE_ABOUT) },
                )
            }

            composable(route = ROUTE_DOWNLOADS) {
                DownloadsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(route = ROUTE_STATISTICS) {
                StatisticsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(route = ROUTE_TASKS) {
                TasksScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(route = ROUTE_UPDATES) {
                LibraryUpdatesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReader = { pkg, novelUrl, chapterUrl ->
                        navController.navigate(
                            "reader?pkg=${Uri.encode(pkg)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                        )
                    },
                    onOpenNovel = { pkg, novelUrl ->
                        navController.navigate(
                            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novelUrl)}"
                        )
                    },
                )
            }

            composable(route = ROUTE_UPDATE_ISSUES) {
                UpdateIssuesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNovel = { pkg, novelUrl ->
                        navController.navigate(
                            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novelUrl)}"
                        )
                    },
                )
            }

            composable(route = ROUTE_ABOUT) {
                AboutSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            navigation(
                startDestination = ROUTE_SETTINGS_ROOT,
                route = "settings_graph",
            ) {
                composable(route = ROUTE_SETTINGS_ROOT) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAppearance = { navController.navigate(ROUTE_SETTINGS_APPEARANCE) },
                        onNavigateToBehavior = { navController.navigate(ROUTE_SETTINGS_BEHAVIOR) },
                        onNavigateToLibrary = { navController.navigate(ROUTE_SETTINGS_LIBRARY) },
                        onNavigateToBrowse = { navController.navigate(ROUTE_SETTINGS_BROWSE) },
                        onNavigateToLanguages = { navController.navigate(ROUTE_SETTINGS_LANGUAGES) },
                        onNavigateToReader = { navController.navigate(ROUTE_SETTINGS_READER) },
                        onNavigateToTts = { navController.navigate(ROUTE_SETTINGS_TTS) },
                        onNavigateToLibraryUpdates = { navController.navigate(ROUTE_SETTINGS_LIBRARY_UPDATE) },
                        onNavigateToBackup = { navController.navigate(ROUTE_SETTINGS_BACKUP) },
                        onNavigateToNovelUpdates = { navController.navigate(ROUTE_SETTINGS_NOVELUPDATES) },
                    )
                }

                composable(route = ROUTE_SETTINGS_APPEARANCE) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    AppearanceSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_BEHAVIOR) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    BehaviorSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_LIBRARY) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    LibrarySettingsScreen(
                        viewModel = vm,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToHiddenCategories = { navController.navigate(ROUTE_SETTINGS_HIDDEN) },
                    )
                }

                composable(route = ROUTE_SETTINGS_HIDDEN) {
                    HiddenCategoriesSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_BROWSE) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    BrowseSettingsScreen(
                        viewModel = vm,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(route = ROUTE_SETTINGS_LANGUAGES) {
                    LanguagesSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSourceLanguages = {
                            navController.navigate(ROUTE_SETTINGS_CONTENT_LANGUAGES)
                        },
                    )
                }

                composable(route = ROUTE_SETTINGS_CONTENT_LANGUAGES) {
                    BrowseLanguagesScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_READER) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    ReaderSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_TTS) {
                    TtsSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToVoice = { lang ->
                            navController.navigate("settings/tts/voice?lang=${Uri.encode(lang)}")
                        },
                    )
                }

                composable(
                    route = ROUTE_SETTINGS_TTS_VOICE,
                    arguments = listOf(navArgument("lang") { type = NavType.StringType }),
                ) {
                    TtsVoicePickerScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_NOVELUPDATES) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    NovelUpdatesSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_BACKUP) {
                    BackupSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_LIBRARY_UPDATE) {
                    LibraryUpdateSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(route = ROUTE_EXTENSION_MANAGE) {
                ExtensionsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSourceSettings = { pkg ->
                        navController.navigate("settings/source/${Uri.encode(pkg)}")
                    },
                )
            }

            composable(
                route = ROUTE_SOURCE_BROWSE,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg") ?: ""
                SourceBrowseScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNovelClick = { novel ->
                        navController.navigate(
                            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novel.url)}"
                        )
                    },
                    onOpenWebView = { url ->
                        navController.navigate("webview?url=${Uri.encode(url)}")
                    },
                    onOpenSourceSettings = {
                        navController.navigate("settings/source/${Uri.encode(pkg)}")
                    },
                )
            }

            composable(
                route = ROUTE_SOURCE_SETTINGS,
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg") ?: ""
                SourceSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToContentLanguages = {
                        navController.navigate("settings/source/${Uri.encode(pkg)}/languages")
                    },
                    onNavigateToLogin = {
                        navController.navigate("login/${Uri.encode(pkg)}")
                    },
                )
            }

            composable(
                route = ROUTE_SOURCE_LANGUAGES,
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) {
                SourceLanguagesScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = ROUTE_NOVEL_DETAIL,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                    navArgument("migrateFrom") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                NovelDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onChapterClick = { pkg, novelUrl, chapterUrl ->
                        navController.navigate(
                            "reader?pkg=${Uri.encode(pkg)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}"
                        )
                    },
                    onOpenWebView = { url ->
                        navController.navigate("webview?url=${Uri.encode(url)}")
                    },
                    onOpenNuSeries = { slug ->
                        navController.navigate("nu_series?slug=${Uri.encode(slug)}")
                    },
                    onNavigateToLogin = { pkg ->
                        navController.navigate("login/${Uri.encode(pkg)}")
                    },
                    onMigrate = { novelId ->
                        navController.navigate("migrate?novelId=$novelId")
                    },
                    onMigrationComplete = { pkg, url ->
                        navController.navigate(
                            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(url)}"
                        ) {
                            popUpTo(TopLevelDestination.Library.route) { inclusive = false }
                        }
                    },
                )
            }

            composable(
                route = ROUTE_MIGRATE,
                arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
            ) { entry ->
                val sourceId = entry.arguments?.getLong("novelId") ?: -1L
                MigrateScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPreviewNovel = { pkg, url ->
                        navController.navigate(
                            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(url)}&migrateFrom=$sourceId"
                        )
                    },
                )
            }

            composable(
                route = ROUTE_READER,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    navArgument("novelUrl") { type = NavType.StringType },
                    navArgument("chapterUrl") { type = NavType.StringType },
                ),
            ) {
                ReaderScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenWebView = { url ->
                        navController.navigate("webview?url=${Uri.encode(url)}")
                    },
                    onOpenTtsSettings = { navController.navigate(ROUTE_SETTINGS_TTS) },
                )
            }

            composable(
                route = ROUTE_WEBVIEW,
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { entry ->
                val url = entry.arguments?.getString("url") ?: ""
                WebViewScreen(url = url, onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = ROUTE_SOURCE_LOGIN,
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) {
                SourceLoginScreen(
                    onNavigateBack = {
                        // Tell the screen that opened login to re-check sign-in state.
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(SOURCE_LOGIN_RESULT_KEY, true)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
