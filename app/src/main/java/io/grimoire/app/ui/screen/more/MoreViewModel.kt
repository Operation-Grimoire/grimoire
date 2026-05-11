package io.grimoire.app.ui.screen.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.dao.ChapterDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(chapterDao: ChapterDao) : ViewModel() {

    val activeDownloadCount = chapterDao.getAllDownloads()
        .map { chapters ->
            chapters.count {
                it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal ||
                    it.downloadStatus == ChapterDownloadStatus.DOWNLOADING.ordinal
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
