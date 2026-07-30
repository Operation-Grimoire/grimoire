package io.grimoire.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.grimoire.app.data.crash.CrashContext
import io.grimoire.app.ui.component.IncognitoBanner
import io.grimoire.app.ui.screen.more.MoreViewModel
import io.grimoire.app.ui.tour.LocalTourRegistry
import io.grimoire.app.ui.tour.TourActionId
import io.grimoire.app.ui.tour.TourKey
import io.grimoire.app.ui.tour.TourOverlay
import io.grimoire.app.ui.tour.TourRegistry
import io.grimoire.app.ui.tour.TourViewModel
import io.grimoire.app.ui.tour.tourById
import io.grimoire.app.ui.tour.tourTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            t == NAV_TARGET_CRASH -> ROUTE_CRASH_REPORT
            // A per-novel notification passes the navigate-ready route directly.
            t.startsWith("novel?") -> t
            else -> null
        }
        if (route != null) {
            // On a cold start the pending target is already set before the
            // NavHost composes its graph, so navigating now throws "graph has
            // not been set". For the saved-crash target that turns a single
            // crash into a relaunch loop: navigate fails → crash handler saves
            // again → relaunch → repeat. Wait for the graph to be ready, and
            // clear the pending target no matter what so a bad target can never
            // re-loop.
            navController.currentBackStackEntryFlow.first()
            runCatching { navController.navigate(route) { launchSingleTop = true } }
        }
        onTargetHandled()
    }

    // An EPUB arriving from an external "Open with" needs the Library tab to be
    // visible so its import-preview dialog has somewhere to render. LibraryScreen
    // is the one that actually consumes the URI and clears the flow.
    val epubUri by pendingEpubUri.collectAsState()
    LaunchedEffect(epubUri) {
        if (epubUri == null) return@LaunchedEffect
        // Same cold-start hazard as the pending target above: an "Open with"
        // EPUB can arrive before the graph is set. Wait for it before navigating.
        navController.currentBackStackEntryFlow.first()
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
                navController.navigate(extensionManageRoute()) { launchSingleTop = true }
            }
        }
    }

    val isTopLevel = TopLevelDestination.entries.any { dest ->
        backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
    } && currentRoute !in routesWithoutBottomBar

    // Library and Browse hide the app nav while multi-selecting so their
    // selection action bar can take that space.
    var libraryInSelection by remember { mutableStateOf(false) }
    var browseInSelection by remember { mutableStateOf(false) }
    val hideNavForSelection =
        (libraryInSelection && currentRoute == TopLevelDestination.Library.route) ||
            (browseInSelection && currentRoute == ROUTE_BROWSE_HOME)

    val scope = rememberCoroutineScope()
    val moreVm: MoreViewModel = hiltViewModel()
    val activeDownloadCount by moreVm.activeDownloadCount.collectAsState()
    val subscribedUpdateCount by moreVm.subscribedUpdateCount.collectAsState()
    val extensionUpdateCount by moreVm.extensionUpdateCount.collectAsState()
    val incognito by moreVm.incognito.collectAsState()

    // Tours. The registry collects target bounds; the controller owns which tour
    // is running + the position. AppNavigation is the one place that can both see
    // the current route and drive navigation, so it bridges the two.
    val tourController = hiltViewModel<TourViewModel>().controller
    val tourState by tourController.state.collectAsState()
    val tourRegistry = remember { TourRegistry() }
    val tourSteps = tourState.tourId?.let { tourById(it).steps }.orEmpty()

    LaunchedEffect(Unit) { tourController.maybeStartOnLaunch() }
    LaunchedEffect(currentRoute) {
        // Record the active screen so a crash report can show where the user was,
        // not just the (often misleading) allocation site of an OOM.
        CrashContext.setRoute(currentRoute)
        tourController.onRouteChanged(currentRoute)
    }
    // Drive navigation to each non-interactive step's screen as it's entered.
    LaunchedEffect(tourState.running, tourState.index) {
        if (!tourState.running) return@LaunchedEffect
        val step = tourSteps.getOrNull(tourState.index) ?: return@LaunchedEffect
        val route = step.route ?: return@LaunchedEffect
        if (step.advanceOnReach) return@LaunchedEffect // user navigates here themselves
        val onRoute = backStack?.destination?.hierarchy?.any { it.route == route } == true
        if (!onRoute) {
            val isTab = TopLevelDestination.entries.any { it.route == route }
            // Step routes are patterns (for the on-route check above); translate
            // the parameterised one into a navigable value so the placeholder
            // never leaks into the screen as a literal "{query}" prefill (#316).
            val navRoute = if (route == ROUTE_EXTENSION_MANAGE) extensionManageRoute() else route
            navController.navigate(navRoute) {
                // Tour navigation is a *fresh* walk: land each tab on its start
                // state and discard saved stacks, so a detail screen the tour
                // opened (extensions) isn't restored when the user taps the tab
                // again after the tour (#316).
                if (isTab) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                    restoreState = false
                }
                launchSingleTop = true
            }
        }
    }
    val onTourAction: (TourActionId) -> Unit = { action ->
        when (action) {
            TourActionId.OpenExtensions ->
                navController.navigate(extensionManageRoute()) { launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalTourRegistry provides tourRegistry) {
        Box(modifier.fillMaxSize()) {
            Scaffold(
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
                        val label = stringResource(dest.labelRes)
                        val isSelected = backStack?.destination?.hierarchy
                            ?.any { it.route == dest.route } == true
                        val tourKey = when (dest) {
                            TopLevelDestination.Library -> TourKey.LibraryTab
                            TopLevelDestination.Browse -> TourKey.BrowseTab
                            TopLevelDestination.More -> TourKey.MoreTab
                        }
                        NavigationBarItem(
                            modifier = Modifier.tourTarget(tourKey),
                            selected = isSelected,
                            onClick = {
                                val alreadyOnTab = isSelected
                                if (!alreadyOnTab) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                                when {
                                    // Re-tapping the active tab is a shortcut, not a no-op:
                                    // Browse jumps to global search, More into settings, and
                                    // Library resumes the most-recently-read novel's reader
                                    // (stacked over its detail page so back lands there).
                                    alreadyOnTab && dest == TopLevelDestination.Browse ->
                                        navController.navigate(ROUTE_GLOBAL_SEARCH) {
                                            launchSingleTop = true
                                        }
                                    alreadyOnTab && dest == TopLevelDestination.More ->
                                        navController.navigate(ROUTE_SETTINGS_ROOT) {
                                            launchSingleTop = true
                                        }
                                    alreadyOnTab && dest == TopLevelDestination.Library ->
                                        scope.launch {
                                            moreVm.resolveResumeReadingRoutes()?.forEach { route ->
                                                navController.navigate(route) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    else ->
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
                                    subscribedUpdateCount > 0
                                val tabIcon = when {
                                    hasUpdates -> if (isSelected) dest.activeSelectedIcon else dest.activeIcon
                                    isSelected -> dest.selectedIcon
                                    else -> dest.icon
                                }
                                when {
                                    updateBadge -> BadgedBox(
                                        badge = { Badge { Text("$extensionUpdateCount") } },
                                    ) {
                                        Icon(tabIcon, contentDescription = label)
                                    }
                                    downloadBadge -> BadgedBox(badge = { Badge() }) {
                                        Icon(tabIcon, contentDescription = label)
                                    }
                                    else -> Icon(tabIcon, contentDescription = label)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
        // App-wide reminder that history recording is paused. Everything is driven off one
        // animated fraction `p` so the open/close reads as a single smooth slide: the
        // status-bar background block grows by `statusBar * p` while the NavHost gives back
        // exactly that much status-bar inset (`consumeWindowInsets`), and the colored strip
        // expands its own height in sync. Net effect: the whole app pushes down by the strip
        // height with no jump.
        // Only the discovery surfaces (Library / Browse / Novel detail) carry the banner;
        // deep pages like the reader keep their full height.
        val bannerVisible = incognito && currentRoute in routesWithIncognitoBanner
        val bannerP by animateFloatAsState(
            targetValue = if (bannerVisible) 1f else 0f,
            animationSpec = tween(BANNER_MS),
            label = "incognitoBanner",
        )
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        if (bannerP > 0f) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(statusBarTop * bannerP)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
            )
        }
        AnimatedVisibility(
            visible = bannerVisible,
            enter = expandVertically(tween(BANNER_MS)) + fadeIn(tween(BANNER_MS)),
            exit = shrinkVertically(tween(BANNER_MS)) + fadeOut(tween(BANNER_MS)),
        ) {
            IncognitoBanner()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .consumeWindowInsets(PaddingValues(top = statusBarTop * bannerP)),
        ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            modifier = Modifier.fillMaxSize(),
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
            browseGraph(
                navController = navController,
                onSelectionActiveChange = { browseInSelection = it },
            )
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
            }

            TourOverlay(
                state = tourState,
                steps = tourSteps,
                registry = tourRegistry,
                onBack = tourController::back,
                onNext = tourController::next,
                onSkip = tourController::skip,
                onAction = onTourAction,
            )
        }
    }
}

