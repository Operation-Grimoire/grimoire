package io.grimoire.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.grimoire.app.ui.screen.BrowseScreen
import io.grimoire.app.ui.screen.ExtensionsScreen
import io.grimoire.app.ui.screen.LibraryScreen

private enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    Library("library", Icons.Default.LocalLibrary, "Library"),
    Browse("browse", Icons.Default.Explore, "Browse"),
    Extensions("extensions", Icons.Default.Extension, "Extensions"),
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
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
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.Library.route) { LibraryScreen() }
            composable(TopLevelDestination.Browse.route) { BrowseScreen() }
            composable(TopLevelDestination.Extensions.route) { ExtensionsScreen() }
        }
    }
}
