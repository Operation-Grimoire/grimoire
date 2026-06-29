package io.grimoire.app.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.dao.BrowsingHistoryDao
import io.grimoire.app.data.local.dao.ReadingHistoryDao
import io.grimoire.app.data.local.entity.BrowsingHistoryEntity
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val readingHistoryDao: ReadingHistoryDao,
    private val browsingHistoryDao: BrowsingHistoryDao,
) : ViewModel() {

    val readingEntries: StateFlow<List<ReadingHistoryEntity>> = readingHistoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val browsingEntries: StateFlow<List<BrowsingHistoryEntity>> = browsingHistoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteReading(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isNotEmpty()) readingHistoryDao.deleteByIds(ids.toList())
    }

    fun clearReading() = viewModelScope.launch { readingHistoryDao.clearAll() }

    fun deleteBrowsing(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isNotEmpty()) browsingHistoryDao.deleteByIds(ids.toList())
    }

    fun clearBrowsing() = viewModelScope.launch { browsingHistoryDao.clearAll() }
}
