package io.grimoire.app.ui.tour

import androidx.annotation.StringRes
import io.grimoire.app.R
/**
 * An anchorable spot in the UI a tour step can point its balloon at. A screen
 * tags the element with [Modifier.tourTarget] and the overlay looks the bounds
 * up by this key. A step with a `null` target renders a centered balloon.
 */
enum class TourKey {
    LibraryTab,
    BrowseTab,
    MoreTab,
    ExtensionManager,
    RepoManager,
    LibraryToolbar,
    LibraryRefresh,
    LibraryMenu,
    BrowseSearch,
    BrowseFilter,
    BrowseNovelUpdates,
    NovelAddToLibrary,
    NovelNotifications,
    NovelDownloads,
    NovelChapterTools,
    NovelRead,
    ReaderSettings,
    ReaderReadAloud,
    ReaderNextChapter,
    ReaderMarkRead,
    ReaderWebView,
}

/**
 * Optional illustration rendered at the top of a balloon, for the steps that
 * want more than a caption (the requested "custom content slot"). Kept as an
 * enum so [TourStep] stays plain data the controller can own.
 */
enum class TourArt { None, Welcome, Done, Tap }

/**
 * An extra button on a step beyond Back/Next/Skip. The behaviour is resolved by
 * the overlay (which has the NavController in scope), so the step list stays
 * free of captured lambdas.
 */
enum class TourActionId(@StringRes val labelRes: Int) {
    OpenExtensions(R.string.tour_open_extensions),
}

/**
 * One stop on the tour.
 *
 * @param target element the balloon points at, or null for a centered balloon.
 * @param route  the top-level route this step lives on. The engine navigates
 *   here when the step is entered (unless [advanceOnReach]).
 * @param advanceOnReach when true the step advances once the user reaches
 *   [route] themselves — an interactive "tap the highlighted thing" step.
 * @param advanceWhenShown advance once this target appears on screen — for
 *   "tap to reveal" steps such as showing the reader's hidden controls.
 */
data class TourStep(
    val target: TourKey?,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val route: String? = null,
    val advanceOnReach: Boolean = false,
    val advanceWhenShown: TourKey? = null,
    val art: TourArt = TourArt.None,
    val actions: List<TourActionId> = emptyList(),
)

/**
 * A self-contained tour: an ordered list of steps plus the metadata the Tours
 * page and the launch logic need. Add a new tour by appending one to
 * [grimoireTours] — the engine, overlay and Tours page are all tour-agnostic.
 *
 * @param id     stable key; also the persistence key for its completion marker.
 * @param version bump to re-show an updated tour to users who finished an old one.
 * @param trigger when it starts on its own, if ever.
 */
data class Tour(
    val id: TourId,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val version: Int,
    val trigger: TourTrigger,
    val steps: List<TourStep>,
) {
    /** The screen a [TourTrigger.FirstVisit] tour lives on; null for the others. */
    val screenRoute: String? get() = (trigger as? TourTrigger.FirstVisit)?.route
}

/** When a [Tour] starts without being picked from the Tours page. */
sealed interface TourTrigger {
    /** Runs on app launch until finished. */
    data object FirstLaunch : TourTrigger

    /** Only runs when started from the Tours page. */
    data object Manual : TourTrigger

    /**
     * Runs the first time the user opens [route]. Used for screens the engine
     * can't navigate to itself (they need a novel), so the tour stays on that
     * screen and ends when the user leaves it.
     *
     * @param hintRes shown on the Tours page while the tour is waiting to run.
     */
    data class FirstVisit(val route: String, @StringRes val hintRes: Int) : TourTrigger
}

/** Stable identifier + persistence key for a [Tour]. */
enum class TourId(val key: String) {
    Welcome("welcome"),
    Library("library"),
    Browse("browse"),
    NovelDetail("novel_detail"),
    Reader("reader"),
}

/** Running state of the engine. [tourId]/[index] are meaningless unless [running]. */
data class TourState(
    val running: Boolean = false,
    val tourId: TourId? = null,
    val index: Int = 0,
)

/** The first-visit tour that belongs on [route], if any. */
internal fun List<Tour>.firstVisitTourFor(route: String?): Tour? =
    if (route == null) null else firstOrNull { it.screenRoute == route }

/** Whether a screen-bound tour should end because the user navigated to [route]. */
internal fun Tour.leftItsScreen(route: String?): Boolean {
    val home = screenRoute ?: return false
    return route != null && route != home
}

/**
 * Watches an [TourStep.advanceWhenShown] target. It fires only when the target
 * appears after being absent, so stepping Back onto the step while the target
 * is already up doesn't bounce straight forward again.
 */
internal class RevealWatch(private val key: TourKey) {
    private var sawHidden = false

    /** Feeds the currently visible targets; true when the step should advance. */
    fun update(visible: Set<TourKey>): Boolean {
        if (key !in visible) {
            sawHidden = true
            return false
        }
        return sawHidden
    }
}
