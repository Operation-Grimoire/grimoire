package io.grimoire.app.ui.screen.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryUpdatesViewModel @Inject constructor(
    private val libraryUpdateDao: LibraryUpdateDao,
    private val scheduler: LibraryUpdateScheduler,
) : ViewModel() {

    val entries: StateFlow<List<LibraryUpdateEntity>> = libraryUpdateDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshNow() = scheduler.triggerOneOff(null)

    fun clearLog() = viewModelScope.launch { libraryUpdateDao.clearAll() }
}
