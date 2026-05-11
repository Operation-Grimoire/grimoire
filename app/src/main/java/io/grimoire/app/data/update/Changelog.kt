package io.grimoire.app.data.update

object Changelog {
    // keyed by versionCode; add new entry each release
    private val entries: Map<Int, String> = mapOf(
        1 to "• Initial release",
    )

    fun since(fromExclusive: Int, toInclusive: Int): String? {
        val relevant = entries.entries
            .filter { (k, _) -> k in (fromExclusive + 1)..toInclusive }
            .sortedByDescending { it.key }
        return if (relevant.isEmpty()) null
        else relevant.joinToString("\n\n") { (_, notes) -> notes }
    }
}
