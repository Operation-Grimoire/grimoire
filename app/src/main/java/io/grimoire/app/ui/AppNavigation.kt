package io.grimoire.app.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import io.grimoire.app.ui.screen.more.MoreScreen
import io.grimoire.app.ui.screen.more.MoreViewModel
import io.grimoire.app.ui.screen.more.statistics.StatisticsScreen
import io.grimoire.app.ui.screen.reader.ReaderScreen
import io.grimoire.app.ui.screen.settings.SettingsScreen
import io.grimoire.app.ui.screen.webview.WebViewScreen
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.about.AboutSettingsScreen
import io.grimoire.app.ui.screen.settings.appearance.AppearanceSettingsScreen
import io.grimoire.app.ui.screen.settings.browse.BrowseSettingsScreen
import io.grimoire.app.ui.screen.settings.hidden.HiddenCategoriesSettingsScreen
import io.grimoire.app.ui.screen.settings.library.LibrarySettingsScreen
import io.grimoire.app.ui.screen.settings.reader.ReaderSettingsScreen

private enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    Library("library", Icons.Default.LocalLibrary, "Library"),
    Browse("browse", Icons.Default.Explore, "Browse"),
    More("more", Icons.Default.MoreHoriz, "More"),
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

private const val ROUTE_BROWSE_HOME = "browse_home"
private const val ROUTE_GLOBAL_SEARCH = "global_search"
private const val ROUTE_EXTENSION_MANAGE = "extensions"
private const val ROUTE_SOURCE_BROWSE = "browse/{pkg}?q={q}"
private const val ROUTE_NOVEL_DETAIL = "novel?pkg={pkg}&url={url}"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_STATISTICS = "statistics"
private const val ROUTE_SETTINGS_ROOT = "settings"
private const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
private const val ROUTE_SETTINGS_LIBRARY = "settings/library"
private const val ROUTE_SETTINGS_BROWSE = "settings/browse"
private const val ROUTE_SETTINGS_READER = "settings/reader"
private const val ROUTE_SETTINGS_ABOUT = "settings/about"
private const val ROUTE_SETTINGS_HIDDEN = "settings/hidden_categories"
private const val ROUTE_READER = "reader?pkg={pkg}&novelUrl={novelUrl}&chapterUrl={chapterUrl}"
private const val ROUTE_WEBVIEW = "webview?url={url}"

private const val POP_MS = 120

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val isTopLevel = TopLevelDestination.entries.any { dest ->
        backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
    }

    val moreVm: MoreViewModel = hiltViewModel()
    val activeDownloadCount by moreVm.activeDownloadCount.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true,
                            onClick = {
                                val alreadyOnTab = backStack?.destination?.hierarchy
                                    ?.any { it.route == dest.route } == true
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
                                val showBadge = dest == TopLevelDestination.More && activeDownloadCount > 0
                                if (showBadge) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(dest.icon, contentDescription = dest.label)
                                    }
                                } else {
                                    Icon(dest.icon, contentDescription = dest.label)
                                }
                            },
                            label = { Text(dest.label) },
                        )
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
                        viewModel = vm,
                    )
                }
                composable(route = ROUTE_GLOBAL_SEARCH) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry(TopLevelDestination.Browse.route) }
                    val vm: BrowseViewModel = hiltViewModel(graphEntry)
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
            }

            composable(route = TopLevelDestination.More.route) {
                MoreScreen(
                    onNavigateToDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                    onNavigateToStatistics = { navController.navigate(ROUTE_STATISTICS) },
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS_ROOT) },
                )
            }

            composable(route = ROUTE_DOWNLOADS) {
                DownloadsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(route = ROUTE_STATISTICS) {
                StatisticsScreen(onNavigateBack = { navController.popBackStack() })
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
                        onNavigateToLibrary = { navController.navigate(ROUTE_SETTINGS_LIBRARY) },
                        onNavigateToBrowse = { navController.navigate(ROUTE_SETTINGS_BROWSE) },
                        onNavigateToReader = { navController.navigate(ROUTE_SETTINGS_READER) },
                        onNavigateToAbout = { navController.navigate(ROUTE_SETTINGS_ABOUT) },
                    )
                }

                composable(route = ROUTE_SETTINGS_APPEARANCE) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    AppearanceSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
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
                    BrowseSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_READER) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    ReaderSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_ABOUT) {
                    AboutSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(route = ROUTE_EXTENSION_MANAGE) {
                ExtensionsScreen(onNavigateBack = { navController.popBackStack() })
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
                )
            }

            composable(
                route = ROUTE_NOVEL_DETAIL,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
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
                )
            }

            composable(
                route = ROUTE_WEBVIEW,
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { entry ->
                val url = entry.arguments?.getString("url") ?: ""
                WebViewScreen(url = url, onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
