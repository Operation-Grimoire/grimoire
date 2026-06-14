package io.grimoire.app.data.athenaeum

import io.grimoire.api.network.HttpSource
import io.grimoire.api.source.Source
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.AthenaeumPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget submission of a scraped novel (+ its chapters) to Athenaeum,
 * gated by the opt-in [AthenaeumPreferences.contributeEnabled] toggle. Never
 * blocks or fails the caller (browse, library add, refresh) — errors are
 * swallowed; the backend dedups repeat submissions. Only HTTP sources are
 * mappable (need a base URL); local/EPUB sources are ignored.
 */
@Singleton
class AthenaeumContributor @Inject constructor(
    private val prefs: AthenaeumPreferences,
    private val client: AthenaeumClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [chapters] must be in reading order so missing numbers can be inferred from neighbours. */
    fun submit(source: Source, novel: NovelEntity, chapters: List<ChapterEntity>) {
        val http = source as? HttpSource ?: return
        scope.launch {
            if (!prefs.contributeEnabled.changes().first()) return@launch
            val items = buildList {
                add(ObservationMapper.series(http.baseUrl, source.lang, novel))
                addAll(ObservationMapper.chapters(http.baseUrl, source.lang, novel, chapters))
            }
            client.submit(items)
        }
    }
}
