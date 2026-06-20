package io.grimoire.app.ui.tour

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
}

/**
 * Optional illustration rendered at the top of a balloon, for the steps that
 * want more than a caption (the requested "custom content slot"). Kept as an
 * enum so [TourStep] stays plain data the controller can own.
 */
enum class TourArt { None, Welcome, Done }

/**
 * An extra button on a step beyond Back/Next/Skip. The behaviour is resolved by
 * the overlay (which has the NavController in scope), so the step list stays
 * free of captured lambdas.
 */
enum class TourActionId(val label: String) {
    OpenExtensions("Open extensions"),
}

/**
 * One stop on the tour.
 *
 * @param target element the balloon points at, or null for a centered balloon.
 * @param route  the top-level route this step lives on. The engine navigates
 *   here when the step is entered (unless [advanceOnReach]).
 * @param advanceOnReach when true the step has no Next button and advances once
 *   the user reaches [route] themselves — an interactive "tap the highlighted
 *   thing" step.
 */
data class TourStep(
    val target: TourKey?,
    val title: String,
    val body: String,
    val route: String? = null,
    val advanceOnReach: Boolean = false,
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
 * @param autoRun whether it runs unprompted on first launch.
 */
data class Tour(
    val id: TourId,
    val title: String,
    val description: String,
    val version: Int,
    val autoRun: Boolean,
    val steps: List<TourStep>,
)

/** Stable identifier + persistence key for a [Tour]. */
enum class TourId(val key: String) {
    Welcome("welcome"),
}

/** Running state of the engine. [tourId]/[index] are meaningless unless [running]. */
data class TourState(
    val running: Boolean = false,
    val tourId: TourId? = null,
    val index: Int = 0,
)
