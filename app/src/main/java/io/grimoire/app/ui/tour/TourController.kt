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

    /** Auto-starts the first eligible auto-run tour the user hasn't finished. */
    fun maybeStartOnLaunch() {
        scope.launch {
            if (_state.value.running) return@launch
            val due = tours.firstOrNull { tour ->
                tour.autoRun &&
                    tourPreferences.completedVersion(tour.id.key).changes().first() < tour.version
            }
            if (due != null) start(due.id)
        }
    }

    /** Starts (or restarts) a tour from its first step. Used by launch + replay. */
    fun start(tourId: TourId) {
        if (tourById(tourId).steps.isEmpty()) return
        _state.value = TourState(running = true, tourId = tourId, index = 0)
    }

    fun next() {
        val s = _state.value
        if (!s.running) return
        if (s.index >= activeSteps.lastIndex) finish() else _state.value = s.copy(index = s.index + 1)
    }

    fun back() {
        val s = _state.value
        if (s.running && s.index > 0) _state.value = s.copy(index = s.index - 1)
    }

    fun skip() = finish()

    private fun finish() {
        val tourId = _state.value.tourId
        _state.value = TourState(running = false)
        if (tourId != null) {
            val version = tourById(tourId).version
            scope.launch { tourPreferences.completedVersion(tourId.key).set(version) }
        }
    }

    /**
     * Advances an interactive ([TourStep.advanceOnReach]) step once the user
     * reaches its route themselves.
     */
    fun onRouteChanged(route: String?) {
        val step = currentStep ?: return
        if (!_state.value.running || !step.advanceOnReach) return
        val want = step.route ?: return
        if (route != null && route.startsWith(want)) next()
    }
}
