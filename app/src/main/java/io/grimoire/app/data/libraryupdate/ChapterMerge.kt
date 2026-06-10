package io.grimoire.app.data.libraryupdate

import io.grimoire.api.model.Chapter
import io.grimoire.app.data.local.entity.ChapterEntity

/**
 * Outcome of matching a freshly fetched chapter list against the rows already
 * stored for a novel. [priors] is aligned index-for-index with the fetched
 * list: the existing row whose state (read / progress / download) the fetched
 * chapter inherits, or null when the chapter has no prior identity and starts
 * fresh.
 */
internal class ChapterMergeResult(
    val priors: List<ChapterEntity?>,
    val matchedByUrl: Int,
    val matchedByName: Int,
    /** Existing rows with read state that matched nothing and will be dropped by the replace. */
    val droppedRead: Int,
)

/**
 * Matches [fetched] chapters to [existing] rows so per-chapter state survives a
 * refresh.
 *
 * URL is the primary identity. Some sources, however, rewrite chapter URLs
 * between fetches (volatile tokens, slug changes, site migrations); with a
 * URL-only join the old row is deleted by the replace and its read state
 * silently vanishes — or appears to "migrate" to whatever chapter now sorts in
 * its place. The fallback pass rescues that case by matching leftover rows on
 * chapter name, but only when the name is unique on both sides, so two
 * same-named chapters can never swap state. Chapters matched by neither pass
 * are genuinely new and inherit nothing.
 */
internal fun matchChapters(
    existing: List<ChapterEntity>,
    fetched: List<Chapter>,
): ChapterMergeResult {
    val priors = arrayOfNulls<ChapterEntity>(fetched.size)
    val claimed = BooleanArray(existing.size)
    val indexByUrl = HashMap<String, Int>(existing.size)
    existing.forEachIndexed { i, e -> indexByUrl.putIfAbsent(e.url, i) }

    var byUrl = 0
    fetched.forEachIndexed { i, ch ->
        val at = indexByUrl[ch.url] ?: return@forEachIndexed
        if (!claimed[at]) {
            priors[i] = existing[at]
            claimed[at] = true
            byUrl++
        }
    }

    var byName = 0
    if (byUrl < fetched.size && existing.size > byUrl) {
        // Unclaimed existing rows keyed by name; ambiguous (repeated) names are
        // unmatchable and stay out of the map.
        val unclaimedIndexByName = existing.indices.asSequence()
            .filterNot { claimed[it] }
            .filter { existing[it].name.isNotBlank() }
            .groupBy { existing[it].name.trim() }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        // The fetched side must be unambiguous too.
        val fetchedNameCounts = fetched.groupingBy { it.name.trim() }.eachCount()
        fetched.forEachIndexed { i, ch ->
            if (priors[i] != null) return@forEachIndexed
            val name = ch.name.trim()
            if (name.isEmpty() || fetchedNameCounts[name] != 1) return@forEachIndexed
            val at = unclaimedIndexByName[name] ?: return@forEachIndexed
            priors[i] = existing[at]
            claimed[at] = true
            byName++
        }
    }

    val droppedRead = existing.indices.count { !claimed[it] && existing[it].read }
    return ChapterMergeResult(priors.toList(), byUrl, byName, droppedRead)
}
