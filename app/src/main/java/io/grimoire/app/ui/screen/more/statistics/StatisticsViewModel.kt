package io.grimoire.app.ui.screen.more.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.entity.LibraryStats
import io.grimoire.app.data.local.entity.ReadingStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    chapterDao: ChapterDao,
) : ViewModel() {

    val readingStats: StateFlow<ReadingStats> = chapterDao.getReadingStats()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ReadingStats(0, 0L, 0, 0, null, null),
        )

    val libraryStats: StateFlow<LibraryStats> = chapterDao.getLibraryStats()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryStats(0, 0, 0, 0),
        )
}
