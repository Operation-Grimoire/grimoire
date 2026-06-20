package io.grimoire.app.ui.screen.tours

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.TourPreferences
import io.grimoire.app.ui.tour.Tour
import io.grimoire.app.ui.tour.TourController
import io.grimoire.app.ui.tour.TourId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ToursViewModel @Inject constructor(
    private val controller: TourController,
    tourPreferences: TourPreferences,
) : ViewModel() {

    val tours: List<Tour> = controller.tours

    /** Ids of tours the user has finished at their current version. */
    val completed: StateFlow<Set<TourId>> = combine(
        tours.map { tour ->
            tourPreferences.completedVersion(tour.id.key)
                .changes()
                .map { version -> tour.id to (version >= tour.version) }
        },
    ) { pairs -> pairs.filter { it.second }.map { it.first }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Starts a tour; the engine navigates away from this screen to run it. */
    fun replay(id: TourId) = controller.start(id)
}
