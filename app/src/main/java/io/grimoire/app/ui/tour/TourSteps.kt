package io.grimoire.app.ui.tour

import io.grimoire.app.R
import io.grimoire.app.ui.ROUTE_EXTENSION_MANAGE
import io.grimoire.app.ui.TopLevelDestination

private val LIBRARY = TopLevelDestination.Library.route
private val BROWSE = TopLevelDestination.Browse.route
private val MORE = TopLevelDestination.More.route

/** The first-run orientation tour: a handful of cross-screen stops. */
private val welcomeTour = Tour(
    id = TourId.Welcome,
    titleRes = R.string.tour_welcome_title,
    descriptionRes = R.string.tour_welcome_description,
    version = 1,
    autoRun = true,
    steps = listOf(
        TourStep(
            target = null,
            titleRes = R.string.tour_step_welcome_title,
            bodyRes = R.string.tour_step_welcome_body,
            route = LIBRARY,
            art = TourArt.Welcome,
        ),
        TourStep(
            target = TourKey.LibraryTab,
            titleRes = R.string.tour_step_library_title,
            bodyRes = R.string.tour_step_library_body,
            route = LIBRARY,
        ),
        TourStep(
            target = TourKey.BrowseTab,
            titleRes = R.string.tour_step_browse_title,
            bodyRes = R.string.tour_step_browse_body,
            route = BROWSE,
            advanceOnReach = true,
        ),
        TourStep(
            target = TourKey.ExtensionManager,
            titleRes = R.string.tour_step_extensions_title,
            bodyRes = R.string.tour_step_extensions_body,
            route = BROWSE,
            actions = listOf(TourActionId.OpenExtensions),
        ),
        TourStep(
            target = TourKey.RepoManager,
            titleRes = R.string.tour_step_repositories_title,
            bodyRes = R.string.tour_step_repositories_body,
            route = ROUTE_EXTENSION_MANAGE,
        ),
        TourStep(
            target = TourKey.MoreTab,
            titleRes = R.string.tour_step_more_title,
            bodyRes = R.string.tour_step_more_body,
            route = MORE,
        ),
        TourStep(
            target = null,
            titleRes = R.string.tour_step_done_title,
            bodyRes = R.string.tour_step_done_body,
            route = LIBRARY,
            art = TourArt.Done,
        ),
    ),
)

/**
 * Every tour the app ships. The engine, overlay and Tours page all read this
 * list, so adding a tour is just appending a [Tour] here.
 */
val grimoireTours: List<Tour> = listOf(
    welcomeTour,
)

fun tourById(id: TourId): Tour = grimoireTours.first { it.id == id }
