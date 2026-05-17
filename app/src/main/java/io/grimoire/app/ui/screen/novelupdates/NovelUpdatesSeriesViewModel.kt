package io.grimoire.app.ui.screen.novelupdates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.novelupdates.NuSeries
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NuSeriesState {
    data object Loading : NuSeriesState
    data class Loaded(val series: NuSeries) : NuSeriesState
    data class Error(val message: String) : NuSeriesState
}

@HiltViewModel
class NovelUpdatesSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelUpdatesInfoRepository,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    private val _state = MutableStateFlow<NuSeriesState>(NuSeriesState.Loading)
    val state: StateFlow<NuSeriesState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = NuSeriesState.Loading
            runCatching { repository.series(slug) }
                .onSuccess { _state.value = NuSeriesState.Loaded(it) }
                .onFailure {
                    _state.value = NuSeriesState.Error(
                        "${it::class.simpleName}: ${it.message ?: "(no message)"}",
                    )
                }
        }
    }
}
