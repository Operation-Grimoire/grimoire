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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.grimoire.app.ui.screen.more.MoreViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val EmptyNavTarget: StateFlow<String?> = MutableStateFlow(null)
private val EmptyEpubUri: StateFlow<Uri?> = MutableStateFlow(null)
private val EmptyAddRepo: StateFlow<PendingAddRepo?> = MutableStateFlow(null)

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    pendingTarget: StateFlow<String?> = EmptyNavTarget,
    onTargetHandled: () -> Unit = {},
    pendingEpubUri: StateFlow<Uri?> = EmptyEpubUri,
    onEpubUriHandled: () -> Unit = {},
    pendingAddRepo: StateFlow<PendingAddRepo?> = EmptyAddRepo,
    onAddRepoHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val target by pendingTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        val route = when {
            t == NAV_TARGET_UPDATES -> ROUTE_UPDATES
            // A per-novel notification passes the navigate-ready route directly.
            t.startsWith("novel?") -> t
            else -> null
        }
        if (route != null) navController.navigate(route) { launchSingleTop = true }
        onTargetHandled()
    }

    // An EPUB arriving from an external "Open with" needs the Library tab to be
    // visible so its import-preview dialog has somewhere to render. LibraryScreen
    // is the one that actually consumes the URI and clears the flow.
    val epubUri by pendingEpubUri.collectAsState()
    LaunchedEffect(epubUri) {
        if (epubUri == null) return@LaunchedEffect
        navController.navigate(TopLevelDestination.Library.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // An add-repo magic link needs the Extensions screen to render the
    // pre-filled "Add repository" dialog. Collect the flow directly so the
    // navigation fires on every emission, including when onNewIntent sets
    // the value while the composition is in onStop.
    //
    // Crucially, skip the navigate when Extensions is already on top:
    // launchSingleTop on the same destination still replaces the back-stack
    // entry, which disposes the existing ExtensionsScreen (losing its in-flight
    // collector + addRepoPrefill state) before the new one can read the value.
    LaunchedEffect(pendingAddRepo) {
        pendingAddRepo.collect { value ->
            if (value != null &&
                navController.currentDestination?.route != ROUTE_EXTENSION_MANAGE
            ) {
                navController.navigate(ROUTE_EXTENSION_MANAGE) { launchSingleTop = true }
            }
        }
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
            libraryDestination(
                navController = navController,
                onSelectionActiveChange = { libraryInSelection = it },
                pendingEpubUri = pendingEpubUri,
                onEpubUriHandled = onEpubUriHandled,
            )
            browseGraph(navController)
            moreDestinations(navController)
            settingsGraph(navController)
            sourceDestinations(
                navController = navController,
                pendingAddRepo = pendingAddRepo,
                onAddRepoHandled = onAddRepoHandled,
            )
            novelDetailDestinations(navController)
            readerDestinations(navController)
        }
    }
}

