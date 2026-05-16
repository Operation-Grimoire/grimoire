package io.grimoire.app.data.novelupdates

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which NovelUpdates series (if any) corresponds to a local novel by
 * its title. Pure and network-free apart from the injected [client] calls, so
 * the scoring logic is unit-testable.
 */
@Singleton
class NovelUpdatesMatcher @Inject constructor(
    private val client: NovelUpdatesClient,
) {

    sealed interface Result {
        data class Auto(val series: NuSeries) : Result
        data class Ambiguous(val candidates: List<NuSearchResult>) : Result
        data object None : Result
    }

    suspend fun match(title: String): Result {
        if (title.isBlank()) return Result.None
        val query = normalize(title)
        val results = client.search(title)
        if (results.isEmpty()) return Result.None

        val scored = results
            .map { it to similarity(query, normalize(it.title)) }
            .sortedByDescending { it.second }

        val best = scored.first()
        val runnerUp = scored.getOrNull(1)?.second ?: 0.0

        // Strong, clearly-best hit: confirm against the series' associated names
        // (NU's highest-signal alt-title field) before accepting automatically.
        if (best.second >= STRONG && best.second - runnerUp >= GAP) {
            val series = runCatching { client.getSeries(best.first.slug) }.getOrNull()
                ?: return Result.Ambiguous(scored.take(AMBIGUOUS_LIMIT).map { it.first })
            val nameHit = (series.associatedNames + series.title)
                .any { similarity(query, normalize(it)) >= STRONG }
            return if (nameHit || best.second >= VERY_STRONG) Result.Auto(series) else
                Result.Ambiguous(scored.take(AMBIGUOUS_LIMIT).map { it.first })
        }

        if (best.second >= WEAK) {
            return Result.Ambiguous(scored.take(AMBIGUOUS_LIMIT).map { it.first })
        }
        return Result.None
    }

    companion object {
        private const val VERY_STRONG = 0.97
        private const val STRONG = 0.85
        private const val GAP = 0.08
        private const val WEAK = 0.45
        private const val AMBIGUOUS_LIMIT = 8

        fun normalize(raw: String): String = raw
            .lowercase()
            .replace(Regex("""\(.*?\)"""), " ")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\b(the|a|an|novel|wn|ln|web|light)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        /** Normalized Levenshtein similarity in 0.0..1.0. */
        fun similarity(a: String, b: String): Double {
            if (a == b) return 1.0
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val dist = levenshtein(a, b)
            val max = maxOf(a.length, b.length)
            return 1.0 - dist.toDouble() / max
        }

        private fun levenshtein(a: String, b: String): Int {
            val prev = IntArray(b.length + 1) { it }
            val curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(
                        curr[j - 1] + 1,
                        prev[j] + 1,
                        prev[j - 1] + cost,
                    )
                }
                System.arraycopy(curr, 0, prev, 0, curr.size)
            }
            return prev[b.length]
        }
    }
}
