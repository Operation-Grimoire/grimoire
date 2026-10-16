package io.grimoire.app.ui.tour

import io.grimoire.app.R
import io.grimoire.app.ui.ROUTE_EXTENSION_MANAGE
import io.grimoire.app.ui.ROUTE_NOVEL_DETAIL
import io.grimoire.app.ui.ROUTE_READER
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
    trigger = TourTrigger.FirstLaunch,
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

/** Library tools: search/filter, refreshing, categories and bulk selection. */
private val libraryTour = Tour(
    id = TourId.Library,
    titleRes = R.string.tour_library_title,
    descriptionRes = R.string.tour_library_description,
    version = 1,
    trigger = TourTrigger.Manual,
    steps = listOf(
        TourStep(
            target = TourKey.LibraryToolbar,
            titleRes = R.string.tour_step_library_find_title,
            bodyRes = R.string.tour_step_library_find_body,
            route = LIBRARY,
        ),
        TourStep(
            target = TourKey.LibraryRefresh,
            titleRes = R.string.tour_step_library_refresh_title,
            bodyRes = R.string.tour_step_library_refresh_body,
            route = LIBRARY,
        ),
        TourStep(
            target = TourKey.LibraryMenu,
            titleRes = R.string.tour_step_library_menu_title,
            bodyRes = R.string.tour_step_library_menu_body,
            route = LIBRARY,
        ),
        TourStep(
            target = null,
            titleRes = R.string.tour_step_library_select_title,
            bodyRes = R.string.tour_step_library_select_body,
            route = LIBRARY,
            art = TourArt.Tap,
        ),
    ),
)

/** Browse home: sources, global search, filtering, NovelUpdates and extensions. */
private val browseTour = Tour(
    id = TourId.Browse,
    titleRes = R.string.tour_browse_title,
    descriptionRes = R.string.tour_browse_description,
    version = 1,
    trigger = TourTrigger.Manual,
    steps = listOf(
        TourStep(
            target = null,
            titleRes = R.string.tour_step_browse_sources_title,
            bodyRes = R.string.tour_step_browse_sources_body,
            route = BROWSE,
        ),
        TourStep(
            target = TourKey.BrowseSearch,
            titleRes = R.string.tour_step_browse_search_title,
            bodyRes = R.string.tour_step_browse_search_body,
            route = BROWSE,
        ),
        TourStep(
            target = TourKey.BrowseFilter,
            titleRes = R.string.tour_step_browse_filter_title,
            bodyRes = R.string.tour_step_browse_filter_body,
            route = BROWSE,
        ),
        TourStep(
            target = TourKey.BrowseNovelUpdates,
            titleRes = R.string.tour_step_browse_novelupdates_title,
            bodyRes = R.string.tour_step_browse_novelupdates_body,
            route = BROWSE,
        ),
        TourStep(
            target = TourKey.ExtensionManager,
            titleRes = R.string.tour_step_browse_extensions_title,
            bodyRes = R.string.tour_step_browse_extensions_body,
            route = BROWSE,
        ),
    ),
)

/** Runs on the first novel page the user opens; it can't open one by itself. */
private val novelDetailTour = Tour(
    id = TourId.NovelDetail,
    titleRes = R.string.tour_novel_title,
    descriptionRes = R.string.tour_novel_description,
    version = 1,
    trigger = TourTrigger.FirstVisit(ROUTE_NOVEL_DETAIL, R.string.tour_novel_hint),
    steps = listOf(
        TourStep(
            target = TourKey.NovelAddToLibrary,
            titleRes = R.string.tour_step_novel_library_title,
            bodyRes = R.string.tour_step_novel_library_body,
        ),
        TourStep(
            target = TourKey.NovelNotifications,
            titleRes = R.string.tour_step_novel_notifications_title,
            bodyRes = R.string.tour_step_novel_notifications_body,
        ),
        TourStep(
            target = TourKey.NovelDownloads,
            titleRes = R.string.tour_step_novel_downloads_title,
            bodyRes = R.string.tour_step_novel_downloads_body,
        ),
        TourStep(
            target = TourKey.NovelChapterTools,
            titleRes = R.string.tour_step_novel_chapters_title,
            bodyRes = R.string.tour_step_novel_chapters_body,
        ),
        TourStep(
            target = TourKey.NovelRead,
            titleRes = R.string.tour_step_novel_read_title,
            bodyRes = R.string.tour_step_novel_read_body,
        ),
    ),
)

/** Runs on the first chapter the user opens. The controls start hidden, so step one reveals them. */
private val readerTour = Tour(
    id = TourId.Reader,
    titleRes = R.string.tour_reader_title,
    descriptionRes = R.string.tour_reader_description,
    version = 1,
    trigger = TourTrigger.FirstVisit(ROUTE_READER, R.string.tour_reader_hint),
    steps = listOf(
        TourStep(
            target = null,
            titleRes = R.string.tour_step_reader_tap_title,
            bodyRes = R.string.tour_step_reader_tap_body,
            advanceWhenShown = TourKey.ReaderSettings,
            art = TourArt.Tap,
        ),
        TourStep(
            target = TourKey.ReaderSettings,
            titleRes = R.string.tour_step_reader_settings_title,
            bodyRes = R.string.tour_step_reader_settings_body,
        ),
        TourStep(
            target = TourKey.ReaderReadAloud,
            titleRes = R.string.tour_step_reader_read_aloud_title,
            bodyRes = R.string.tour_step_reader_read_aloud_body,
        ),
        TourStep(
            target = TourKey.ReaderNextChapter,
            titleRes = R.string.tour_step_reader_chapters_title,
            bodyRes = R.string.tour_step_reader_chapters_body,
        ),
        TourStep(
            target = TourKey.ReaderMarkRead,
            titleRes = R.string.tour_step_reader_mark_read_title,
            bodyRes = R.string.tour_step_reader_mark_read_body,
        ),
        TourStep(
            target = TourKey.ReaderWebView,
            titleRes = R.string.tour_step_reader_webview_title,
            bodyRes = R.string.tour_step_reader_webview_body,
        ),
    ),
)

/**
 * Every tour the app ships. The engine, overlay and Tours page all read this
 * list, so adding a tour is just appending a [Tour] here.
 */
val grimoireTours: List<Tour> = listOf(
    welcomeTour,
    libraryTour,
    browseTour,
    novelDetailTour,
    readerTour,
)

fun tourById(id: TourId): Tour = grimoireTours.first { it.id == id }
