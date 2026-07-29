package io.grimoire.app.data.libraryupdate

import io.grimoire.api.model.novel.Chapter
import io.grimoire.app.data.local.dao.ChapterMetaUpdate
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

/**
 * The in-place reconciliation [ChapterDao.reconcileChapters][io.grimoire.app.data.local.dao.ChapterDao.reconcileChapters]
 * applies to bring a novel's stored chapters in line with a freshly fetched list
 * without round-tripping any chapter's `downloadedContent` through memory.
 */
internal class ChapterReconcilePlan(
    /** Ids of existing rows the source no longer lists. */
    val deleteIds: List<Long>,
    /** Metadata-only rewrites of rows whose state (read flag, download) is kept. */
    val updates: List<ChapterMetaUpdate>,
    /** Genuinely new chapters, inserted with fresh (unread, undownloaded) state. */
    val inserts: List<ChapterEntity>,
)

/**
 * True when a reconcile would delete a suspicious fraction of an established
 * chapter list — the signature of a silently truncated fetch (a throttled site
 * serving a partial list) rather than a real mass removal. Callers keep the
 * existing list and warn instead of deleting. Short lists are exempt: sources
 * legitimately restructure small catalogs, and the blast radius is tiny.
 */
internal fun isSuspectTruncation(existingCount: Int, deleteCount: Int): Boolean =
    existingCount >= SUSPECT_TRUNCATION_MIN_LIST && deleteCount * 2 > existingCount

internal const val SUSPECT_TRUNCATION_MIN_LIST = 20

/**
 * Ceiling on how many "new" chapters one sync may log to the updates feed for a
 * single novel. A count above it is almost always chapters being *re-detected*
 * after an earlier truncated sync deleted them (no translator drops 150+
 * chapters of one novel in the hours between syncs), so the feed entries are
 * suppressed and a warning is recorded instead. The chapters themselves are
 * still reconciled into the database either way.
 */
internal const val MAX_LOGGED_NEW_CHAPTERS = 150

/**
 * Turns a [matchChapters] result into the delete / update / insert sets a refresh
 * applies. Each fetched chapter with a prior becomes an in-place metadata [update]
 * of that row (its read state and downloaded content stay put); each fetched
 * chapter with no prior is a fresh [insert]; every existing row that matched
 * nothing is [delete]d. Pure — no `downloadedContent` is touched.
 */
internal fun buildReconcilePlan(
    novelId: Long,
    existing: List<ChapterEntity>,
    fetched: List<Chapter>,
    merge: ChapterMergeResult,
): ChapterReconcilePlan {
    val claimedIds = merge.priors.mapNotNullTo(HashSet()) { it?.id }
    val deleteIds = existing.asSequence()
        .filterNot { it.id in claimedIds }
        .map { it.id }
        .toList()
    val updates = ArrayList<ChapterMetaUpdate>(fetched.size)
    val inserts = ArrayList<ChapterEntity>()
    fetched.forEachIndexed { i, ch ->
        val prev = merge.priors[i]
        if (prev != null) {
            updates += ChapterMetaUpdate(
                id = prev.id,
                url = ch.url,
                name = ch.name,
                uploadDate = ch.uploadDate,
                chapterNumber = ch.chapterNumber,
                translator = ch.translator,
                locked = ch.locked,
            )
        } else {
            inserts += ChapterEntity(
                novelId = novelId,
                url = ch.url,
                name = ch.name,
                uploadDate = ch.uploadDate,
                chapterNumber = ch.chapterNumber,
                translator = ch.translator,
                locked = ch.locked,
            )
        }
    }
    return ChapterReconcilePlan(deleteIds, updates, inserts)
}
