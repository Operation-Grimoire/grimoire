package io.grimoire.app.data.local

import android.util.Log
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.preferences.PreferenceStore
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.LoadedExtension
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time re-key of `NovelEntity.sourceId` from the legacy hand-assigned id to
 * the package-derived id (see `sourceIdFor`), so pre-existing libraries keep
 * resolving to their source after the switch.
 *
 * Each novel is matched first by the legacy id its installed source declares
 * (covers relative-URL novels while the old APKs are installed), then by URL host
 * (covers the id collision being fixed). Runs once; a novel whose source isn't
 * installed keeps its old id.
 */
@Singleton
class SourceIdMigrator @Inject constructor(
    private val store: PreferenceStore,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
) {
    private val migrated = store.getBoolean(MIGRATED_KEY, false)

    suspend fun migrateIfNeeded() {
        if (migrated.changes().first()) return
        extensionManager.awaitReady()
        val loaded = extensionManager.extensions.value

        // The legacy declared `Source.id` was removed from the API; old DB rows
        // are now bridged to their extension purely by URL host (see ownerFor).
        val ownersByLegacyId: Map<Long, List<LoadedExtension>> = emptyMap()

        var remapped = 0
        var unresolved = 0
        for (novel in novelDao.getAll()) {
            if (novel.sourceId == LOCAL_SOURCE_ID) continue
            val owner = ownerFor(novel.sourceId, novel.url, ownersByLegacyId, loaded)
            if (owner == null) {
                unresolved++
                continue
            }
            if (owner.id != novel.sourceId) {
                novelDao.updateSourceId(novel.id, owner.id)
                remapped++
            }
        }
        Log.i(TAG, "source-id migration: remapped=$remapped unresolved=$unresolved")
        migrated.set(true)
    }

    /** Owner of a saved novel: by declared legacy id, else by URL host. Null if not installed. */
    private fun ownerFor(
        sourceId: Long,
        novelUrl: String,
        ownersByLegacyId: Map<Long, List<LoadedExtension>>,
        loaded: List<LoadedExtension>,
    ): LoadedExtension? {
        val byLegacy = ownersByLegacyId[sourceId].orEmpty()
        if (byLegacy.size == 1) return byLegacy.single()
        // Missing or colliding declared id: the URL host is authoritative.
        val candidates = if (byLegacy.isEmpty()) loaded else byLegacy
        return candidates.singleOrNull { sameHost(it, novelUrl) }
    }

    private fun sameHost(ext: LoadedExtension, novelUrl: String): Boolean {
        val baseUrl = ext.source.javaClass.getAnnotation(SourceInfo::class.java)?.baseUrl ?: return false
        val novelHost = novelUrl.toHttpUrlOrNull()?.host ?: return false
        val baseHost = baseUrl.toHttpUrlOrNull()?.host ?: return false
        return novelHost.equals(baseHost, ignoreCase = true)
    }

    private companion object {
        const val TAG = "SourceIdMigrator"
        const val MIGRATED_KEY = "source_ids_pkg_derived_v1"
    }
}
