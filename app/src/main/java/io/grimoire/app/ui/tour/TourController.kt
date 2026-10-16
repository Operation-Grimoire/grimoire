package io.grimoire.app.ui.tour

import io.grimoire.app.data.preferences.TourPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives whichever tour is currently running. Tour-agnostic: it owns only the
 * active tour id + position and the per-tour completion markers. UI reads
 * [state] / [activeSteps] and calls [start]/[next]/[back]/[skip]; AppNavigation
 * navigates per step and reports route changes via [onRouteChanged].
 */
@Singleton
class TourController @Inject constructor(
    private val tourPreferences: TourPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val tours: List<Tour> = grimoireTours

    private val _state = MutableStateFlow(TourState())
    val state: StateFlow<TourState> = _state.asStateFlow()

    /** Steps of the running tour, or empty when nothing is running. */
    val activeSteps: List<TourStep>
        get() = _state.value.tourId?.let { tourById(it).steps }.orEmpty()

    private val currentStep: TourStep? get() = activeSteps.getOrNull(_state.value.index)

    /** Watches the current step's [TourStep.advanceWhenShown] target, if it has one. */
    private var revealWatch: RevealWatch? = null

    /** Latest route from [onRouteChanged], so a first-visit tour can't start after the user left. */
    private var currentRoute: String? = null

    /**
     * Completion markers this session wrote, read ahead of the store so a
     * check never sees a value that's still being saved.
     */
    private val writtenVersions = mutableMapOf<TourId, Int>()

    /** Auto-starts the first eligible launch tour the user hasn't finished. */
    fun maybeStartOnLaunch() {
        scope.launch {
            if (_state.value.running) return@launch
            val due = tours.firstOrNull { tour ->
                tour.trigger == TourTrigger.FirstLaunch && !isFinished(tour)
            }
            if (due != null) start(due.id)
        }
    }

    /** Starts (or restarts) a tour from its first step. Used by launch + replay. */
    fun start(tourId: TourId) {
        if (tourById(tourId).steps.isEmpty()) return
        setState(TourState(running = true, tourId = tourId, index = 0))
    }

    /** Clears a first-visit tour's completion so it runs again on its screen. */
    fun rearm(tourId: TourId) = saveCompleted(tourId, 0)

    fun next() {
        val s = _state.value
        if (!s.running) return
        if (s.index >= activeSteps.lastIndex) finish() else setState(s.copy(index = s.index + 1))
    }

    fun back() {
        val s = _state.value
        if (s.running && s.index > 0) setState(s.copy(index = s.index - 1))
    }

    fun skip() = finish()

    private fun finish() {
        val tourId = _state.value.tourId
        setState(TourState(running = false))
        if (tourId != null) saveCompleted(tourId, tourById(tourId).version)
        // A first visit made while this tour ran gets its tour now.
        maybeStartFirstVisit(currentRoute)
    }

    private fun saveCompleted(tourId: TourId, version: Int) {
        writtenVersions[tourId] = version
        scope.launch { tourPreferences.completedVersion(tourId.key).set(version) }
    }

    private fun setState(state: TourState) {
        _state.value = state
        revealWatch = currentStep?.advanceWhenShown?.let(::RevealWatch)
    }

    private suspend fun isFinished(tour: Tour): Boolean {
        val version = writtenVersions[tour.id]
            ?: tourPreferences.completedVersion(tour.id.key).changes().first()
        return version >= tour.version
    }

    /**
     * Reacts to navigation: advances an interactive ([TourStep.advanceOnReach])
     * step once the user reaches its route, ends a screen-bound tour when the
     * user leaves its screen, and starts a first-visit tour on its screen.
     */
    fun onRouteChanged(route: String?) {
        currentRoute = route
        val s = _state.value
        val running = s.tourId?.takeIf { s.running }?.let(::tourById)
        when {
            running == null -> maybeStartFirstVisit(route)
            running.leftItsScreen(route) -> finish()
            else -> {
                val step = currentStep ?: return
                val want = step.route ?: return
                if (step.advanceOnReach && route != null && route.startsWith(want)) next()
            }
        }
    }

    private fun maybeStartFirstVisit(route: String?) {
        val visitTour = tours.firstVisitTourFor(route) ?: return
        scope.launch {
            if (isFinished(visitTour)) return@launch
            if (!_state.value.running && currentRoute == route) start(visitTour.id)
        }
    }

    /** Feeds the targets currently on screen, for [TourStep.advanceWhenShown] steps. */
    fun onTargetsChanged(visible: Set<TourKey>) {
        if (revealWatch?.update(visible) == true) next()
    }
}
