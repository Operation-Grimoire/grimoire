package io.grimoire.app.data.local

import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single, app-scoped source of the favorited (library) novels.
 *
 * Both the Library screen's ViewModel and the [io.grimoire.app.data.cache.CoverPreloader]
 * need the favorites list and used to each open their own Room `Flow` on
 * `SELECT * FROM novels WHERE favorite = 1` — two independent query observers that both
 * re-ran on every write to the novels table. Sharing one eagerly-started [StateFlow]
 * collapses that to a single observer for the whole process.
 *
 * Stays `null` until the first database emission so consumers can tell "still loading"
 * apart from "no favorites yet".
 */
@Singleton
class LibraryFavorites @Inject constructor(
    novelDao: NovelDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val favorites: StateFlow<List<NovelEntity>?> = novelDao.getFavorites()
        .stateIn(scope, SharingStarted.Eagerly, null)
}
