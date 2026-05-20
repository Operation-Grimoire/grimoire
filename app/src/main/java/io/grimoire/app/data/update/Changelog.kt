package io.grimoire.app.data.update

// Offline fallback for the "what's new" dialog. The primary source is the
// GitHub release body fetched at launch (see AppUpdateChecker); this map only
// kicks in when the network fetch fails. Entries are optional — populate them
// for stable versionCodes where an offline-friendly note is desirable. Per-beta
// entries are unnecessary; betas always rely on the GitHub `beta` tag body.
object Changelog {
    // keyed by versionCode
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
