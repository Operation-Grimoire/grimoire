package io.grimoire.app.data.local

import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.NovelEntity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val SOURCE_ID_MIGRATION_KEY_PREFIX = "source_ids_pkg_derived_"
internal const val SOURCE_ID_MIGRATION_KEY = SOURCE_ID_MIGRATION_KEY_PREFIX + "v2"

internal data class InstalledSourceIdentity(val id: Long, val baseUrl: String?)
internal data class SourceIdChange(val novelId: Long, val sourceId: Long)
internal data class SourceIdMigrationPlan(val changes: List<SourceIdChange>, val unresolved: Int) {
    val complete: Boolean get() = unresolved == 0
}

internal fun resolveSourceId(sourceId: Long, novelUrl: String, sources: List<InstalledSourceIdentity>): Long? {
    if (sourceId == LOCAL_SOURCE_ID || sources.any { it.id == sourceId }) return sourceId
    val host = novelUrl.toHttpUrlOrNull()?.host ?: return null
    return sources.singleOrNull { it.baseUrl?.toHttpUrlOrNull()?.host == host }?.id
}

internal fun planSourceIdMigration(
    novels: List<NovelEntity>,
    sources: List<InstalledSourceIdentity>,
): SourceIdMigrationPlan {
    val resolved = novels.map { it to resolveSourceId(it.sourceId, it.url, sources) }
    val targetCounts = resolved.mapNotNull { (novel, target) -> target?.let { it to novel.url } }
        .groupingBy { it }.eachCount()
    val changes = mutableListOf<SourceIdChange>()
    var unresolved = 0
    for ((novel, target) in resolved) {
        when {
            target == null -> unresolved++
            target == novel.sourceId -> Unit
            // Preserve conflicting rows rather than discard either set of reading state.
            targetCounts[target to novel.url] != 1 -> unresolved++
            else -> changes += SourceIdChange(novel.id, target)
        }
    }
    return SourceIdMigrationPlan(changes, unresolved)
}
