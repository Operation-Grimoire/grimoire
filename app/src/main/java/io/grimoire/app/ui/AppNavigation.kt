package io.grimoire.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.grimoire.app.ui.screen.BrowseScreen
import io.grimoire.app.ui.screen.ExtensionsScreen
import io.grimoire.app.ui.screen.LibraryScreen
import io.grimoire.app.ui.screen.SourceBrowseScreen

private enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    Library("library", Icons.Default.LocalLibrary, "Library"),
    Browse("browse", Icons.Default.Explore, "Browse"),
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

private const val ROUTE_EXTENSION_MANAGE = "extensions"
private const val ROUTE_SOURCE_BROWSE = "browse/{pkg}"

private const val FADE_MS = 200
private const val SLIDE_MS = 300

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
            enterTransition = { slideInHorizontally(tween(SLIDE_MS)) { it } + fadeIn(tween(SLIDE_MS)) },
            exitTransition = { slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeOut(tween(SLIDE_MS)) },
            popEnterTransition = { slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeIn(tween(SLIDE_MS)) },
            popExitTransition = { slideOutHorizontally(tween(SLIDE_MS)) { it } + fadeOut(tween(SLIDE_MS)) },
        ) {
            composable(
                route = TopLevelDestination.Library.route,
                enterTransition = { fadeIn(tween(FADE_MS)) },
                exitTransition = {
                    if (targetState.destination.route in topLevelRoutes) fadeOut(tween(FADE_MS))
                    else slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeOut(tween(SLIDE_MS))
                },
                popEnterTransition = { fadeIn(tween(FADE_MS)) },
                popExitTransition = { fadeOut(tween(FADE_MS)) },
            ) {
                LibraryScreen()
            }

            composable(
                route = TopLevelDestination.Browse.route,
                enterTransition = { fadeIn(tween(FADE_MS)) },
                exitTransition = {
                    if (targetState.destination.route in topLevelRoutes) fadeOut(tween(FADE_MS))
                    else slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeOut(tween(SLIDE_MS))
                },
                popEnterTransition = { slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeIn(tween(SLIDE_MS)) },
                popExitTransition = { fadeOut(tween(FADE_MS)) },
            ) {
                BrowseScreen(
                    onNavigateToManage = { navController.navigate(ROUTE_EXTENSION_MANAGE) },
                    onNavigateToSource = { pkg ->
                        navController.navigate("browse/$pkg")
                    },
                )
            }

            composable(route = ROUTE_EXTENSION_MANAGE) {
                ExtensionsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = ROUTE_SOURCE_BROWSE,
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) {
                SourceBrowseScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
