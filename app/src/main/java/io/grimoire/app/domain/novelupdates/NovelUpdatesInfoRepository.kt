package io.grimoire.app.domain.novelupdates

import io.grimoire.app.data.novelupdates.NovelUpdatesClient
import io.grimoire.app.data.novelupdates.NovelUpdatesMatcher
import io.grimoire.app.data.novelupdates.NuBrowseFilter
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.data.novelupdates.NuListingFilter
import io.grimoire.app.data.novelupdates.NuRankingType
import io.grimoire.app.data.preferences.NovelUpdatesPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the NovelUpdates matcher + client and persists manual links so
 * the novel detail screen only has to ask for a single [NuInfoState].
 */
@Singleton
class NovelUpdatesInfoRepository @Inject constructor(
    private val client: NovelUpdatesClient,
    private val matcher: NovelUpdatesMatcher,
    private val preferences: NovelUpdatesPreferences,
) {

    suspend fun isEnabled(): Boolean = preferences.enabled.changes().first()

    /**
     * True once a novel has been resolved/linked before, so the detail screen
     * can auto-restore it instead of showing the "Load" button again.
     */
    suspend fun hasStoredLink(pkg: String, novelUrl: String): Boolean =
        manualLink(pkg, novelUrl) != null

    suspend fun infoFor(pkg: String, novelUrl: String, title: String): NuInfoState {
        if (!preferences.enabled.changes().first()) return NuInfoState.Disabled

        val linkedSlug = manualLink(pkg, novelUrl)
        if (linkedSlug != null) {
            return runCatching { NuInfoState.Matched(client.getSeries(linkedSlug)) }
                .getOrElse { NuInfoState.Error(it.shortMessage()) }
        }

        return runCatching {
            when (val r = matcher.match(title)) {
                is NovelUpdatesMatcher.Result.Auto -> {
                    // Remember the resolved series so reopening the novel
                    // restores it without prompting to load again.
                    setManualLink(pkg, novelUrl, r.series.slug)
                    NuInfoState.Matched(r.series)
                }
                is NovelUpdatesMatcher.Result.Ambiguous -> NuInfoState.Ambiguous(r.candidates)
                NovelUpdatesMatcher.Result.None -> NuInfoState.NotFound
            }
        }.getOrElse { NuInfoState.Error(it.shortMessage()) }
    }

    /** Pins a novel to a specific NU slug and returns the resolved series. */
    suspend fun link(pkg: String, novelUrl: String, slug: String): NuInfoState {
        return runCatching {
            val series = client.getSeries(slug)
            setManualLink(pkg, novelUrl, series.slug)
            NuInfoState.Matched(series)
        }.getOrElse { NuInfoState.Error(it.shortMessage()) }
    }

    suspend fun search(query: String) = runCatching { client.search(query) }.getOrDefault(emptyList())

    /** Series Finder search/filter listing for the in-app NU browser. */
    suspend fun finder(filter: NuBrowseFilter, page: Int) = client.finder(filter, page)

    /** Series Ranking page for the in-app NU browser. */
    suspend fun ranking(type: NuRankingType, filter: NuListingFilter, page: Int) =
        client.ranking(type, filter, page)

    /** Latest Series page for the in-app NU browser. */
    suspend fun latest(filter: NuListingFilter, page: Int) = client.latest(filter, page)

    /** Fetches one NU series page (used by the standalone NU series screen). */
    suspend fun series(slug: String) = client.getSeries(slug)

    private suspend fun manualLink(pkg: String, novelUrl: String): String? =
        preferences.manualLinks.changes().first()[key(pkg, novelUrl)]

    private suspend fun setManualLink(pkg: String, novelUrl: String, slug: String) {
        val current = preferences.manualLinks.changes().first().toMutableMap()
        current[key(pkg, novelUrl)] = slug
        preferences.manualLinks.set(current)
    }

    private fun key(pkg: String, novelUrl: String) = "$pkg|$novelUrl"

    private fun Throwable.shortMessage() =
        "${this::class.simpleName}: ${message ?: "(no message)"}"
}
