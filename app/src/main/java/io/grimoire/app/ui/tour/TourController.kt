package io.grimoire.app.ui.tour

import io.grimoire.app.data.preferences.AppPreferences
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
 * Drives the onboarding tour: owns the step list, the current position, and the
 * persisted "completed" marker. UI (AppNavigation + TourOverlay) reads [state]
 * and calls [next]/[back]/[skip]; AppNavigation reacts to step changes by
 * navigating to each step's route and reports route changes back via
 * [onRouteChanged] so interactive steps can advance.
 */
@Singleton
class TourController @Inject constructor(
    private val appPreferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val steps: List<TourStep> = grimoireTourSteps

    private val _state = MutableStateFlow(TourState())
    val state: StateFlow<TourState> = _state.asStateFlow()

    private val currentStep: TourStep? get() = steps.getOrNull(_state.value.index)

    /** Starts the tour on launch if the user hasn't finished the current one. */
    fun maybeStartOnLaunch() {
        scope.launch {
            val completed = appPreferences.tourCompletedVersion.changes().first()
            if (completed < CURRENT_TOUR_VERSION && !_state.value.running) start()
        }
    }

    /** Starts (or restarts) the tour from the first step — also used by replay. */
    fun start() {
        if (steps.isEmpty()) return
        _state.value = TourState(running = true, index = 0)
    }

    fun next() {
        val s = _state.value
        if (!s.running) return
        if (s.index >= steps.lastIndex) finish() else _state.value = s.copy(index = s.index + 1)
    }

    fun back() {
        val s = _state.value
        if (s.running && s.index > 0) _state.value = s.copy(index = s.index - 1)
    }

    /** Skip and Done both end the tour and mark it complete. */
    fun skip() = finish()

    private fun finish() {
        _state.value = TourState(running = false)
        scope.launch { appPreferences.tourCompletedVersion.set(CURRENT_TOUR_VERSION) }
    }

    /**
     * Fed the current top-level route by AppNavigation. Advances an interactive
     * ([TourStep.advanceOnReach]) step once the user navigates to its route.
     */
    fun onRouteChanged(route: String?) {
        val step = currentStep ?: return
        if (!_state.value.running || !step.advanceOnReach) return
        val want = step.route ?: return
        if (route != null && route.startsWith(want)) next()
    }
}
