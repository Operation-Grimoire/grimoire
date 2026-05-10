package io.grimoire.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.browse.BrowseScreen
import io.grimoire.app.ui.screen.browse.SourceBrowseScreen
import io.grimoire.app.ui.screen.extensions.ExtensionsScreen
import io.grimoire.app.ui.screen.library.LibraryScreen
import io.grimoire.app.ui.screen.settings.SettingsScreen
import io.grimoire.app.ui.screen.settings.about.AboutSettingsScreen
import io.grimoire.app.ui.screen.settings.appearance.AppearanceSettingsScreen
import io.grimoire.app.ui.screen.settings.browse.BrowseSettingsScreen
import io.grimoire.app.ui.screen.settings.reader.ReaderSettingsScreen

private enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    Library("library", Icons.Default.LocalLibrary, "Library"),
    Browse("browse", Icons.Default.Explore, "Browse"),
    Settings("settings", Icons.Default.Settings, "Settings"),
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

private const val ROUTE_EXTENSION_MANAGE = "extensions"
private const val ROUTE_SOURCE_BROWSE = "browse/{pkg}"
private const val ROUTE_SETTINGS_APPEARANCE = "settings/appearance"
private const val ROUTE_SETTINGS_BROWSE = "settings/browse"
private const val ROUTE_SETTINGS_READER = "settings/reader"
private const val ROUTE_SETTINGS_ABOUT = "settings/about"

private const val POP_MS = 120

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val isTopLevel = currentRoute in topLevelRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
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
                LibraryScreen()
            }

            composable(route = TopLevelDestination.Browse.route) {
                BrowseScreen(
                    onNavigateToManage = { navController.navigate(ROUTE_EXTENSION_MANAGE) },
                    onNavigateToSource = { pkg -> navController.navigate("browse/$pkg") },
                )
            }

            navigation(
                startDestination = TopLevelDestination.Settings.route,
                route = "settings_graph",
            ) {
                composable(route = TopLevelDestination.Settings.route) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    SettingsScreen(
                        onNavigateToAppearance = { navController.navigate(ROUTE_SETTINGS_APPEARANCE) },
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

                composable(route = ROUTE_SETTINGS_BROWSE) { entry ->
                    val graphEntry = remember(entry) { navController.getBackStackEntry("settings_graph") }
                    val vm: SettingsViewModel = hiltViewModel(graphEntry)
                    BrowseSettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
                }

                composable(route = ROUTE_SETTINGS_READER) {
                    ReaderSettingsScreen(onNavigateBack = { navController.popBackStack() })
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
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) {
                SourceBrowseScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
