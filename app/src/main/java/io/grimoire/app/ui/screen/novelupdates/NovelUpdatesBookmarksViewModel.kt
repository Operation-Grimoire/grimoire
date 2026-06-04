package io.grimoire.app.ui.screen.novelupdates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.dao.NuBookmarkDao
import io.grimoire.app.data.novelupdates.NuSearchResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NovelUpdatesBookmarksViewModel @Inject constructor(
    private val bookmarkDao: NuBookmarkDao,
) : ViewModel() {

    /** Bookmarks as search-result cards so the shared NU cover grid renders them. */
    val bookmarks: StateFlow<List<NuSearchResult>> = bookmarkDao.getAll()
        .map { list ->
            list.map { b ->
                NuSearchResult(title = b.title, slug = b.slug, url = b.url, coverUrl = b.coverUrl)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(slug: String) = viewModelScope.launch { bookmarkDao.delete(slug) }
}
