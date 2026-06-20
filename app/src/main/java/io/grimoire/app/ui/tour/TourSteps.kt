package io.grimoire.app.ui.tour

import io.grimoire.app.ui.TopLevelDestination

/**
 * Bump when the step list changes meaningfully enough to re-show the tour to
 * users who already finished the previous one. Persisted as
 * `tour_completed_version`; a tour runs while the stored value is lower.
 */
const val CURRENT_TOUR_VERSION = 1

private val LIBRARY = TopLevelDestination.Library.route
private val BROWSE = TopLevelDestination.Browse.route
private val MORE = TopLevelDestination.More.route

/**
 * The guided tour. Cross-screen: the engine navigates to each step's [route],
 * the balloon morphs from target to target. Keep it short — a handful of
 * orientation stops, not a manual.
 */
val grimoireTourSteps: List<TourStep> = listOf(
    TourStep(
        target = null,
        title = "Welcome to Grimoire",
        body = "A quick tour of the essentials — takes about 20 seconds.",
        route = LIBRARY,
        art = TourArt.Welcome,
    ),
    TourStep(
        target = TourKey.LibraryTab,
        title = "Your library",
        body = "Novels you add live here, organised into categories.",
        route = LIBRARY,
    ),
    TourStep(
        target = TourKey.BrowseTab,
        title = "Browse",
        body = "Tap Browse to find novels across your sources.",
        route = BROWSE,
        advanceOnReach = true,
    ),
    TourStep(
        target = TourKey.ExtensionManager,
        title = "Sources & extensions",
        body = "Add, update and configure sources from here.",
        route = BROWSE,
        actions = listOf(TourActionId.OpenExtensions),
    ),
    TourStep(
        target = TourKey.MoreTab,
        title = "Everything else",
        body = "Downloads, statistics, backups and settings are under More.",
        route = MORE,
    ),
    TourStep(
        target = null,
        title = "You're all set",
        body = "Replay this tour any time from Settings → About.",
        route = LIBRARY,
        art = TourArt.Done,
    ),
)
