package io.grimoire.app.ui.tour

import io.grimoire.app.ui.ROUTE_EXTENSION_MANAGE
import io.grimoire.app.ui.TopLevelDestination

private val LIBRARY = TopLevelDestination.Library.route
private val BROWSE = TopLevelDestination.Browse.route
private val MORE = TopLevelDestination.More.route

/** The first-run orientation tour: a handful of cross-screen stops. */
private val welcomeTour = Tour(
    id = TourId.Welcome,
    title = "Welcome tour",
    description = "The essentials: library, browsing, sources and settings.",
    version = 1,
    autoRun = true,
    steps = listOf(
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
            body = "Sources install from here. Open it to see what's available.",
            route = BROWSE,
            actions = listOf(TourActionId.OpenExtensions),
        ),
        TourStep(
            target = TourKey.RepoManager,
            title = "Repositories",
            body = "Sources come from repositories. The official one is already " +
                "added — tap here to manage them or add your own.",
            route = ROUTE_EXTENSION_MANAGE,
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
            body = "Replay any tour from More → Tours.",
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
