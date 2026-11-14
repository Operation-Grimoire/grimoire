package io.grimoire.app.data.local

import android.util.Log
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.preferences.PreferenceStore
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Re-key legacy source ids by URL host; missing or ambiguous sources stay pending. */
@Singleton
class SourceIdMigrator @Inject constructor(
    store: PreferenceStore,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
) {
    private val migrated = store.getBoolean(SOURCE_ID_MIGRATION_KEY, false)
    private val migrationMutex = Mutex()

    suspend fun migrateIfNeeded() = migrationMutex.withLock {
        if (migrated.changes().first()) return@withLock
        val sources = scanSources()
        val plan = planSourceIdMigration(novelDao.getAll(), sources)
        for (change in plan.changes) novelDao.updateSourceId(change.novelId, change.sourceId)
        Log.i("SourceIdMigrator", "source-id migration: remapped=${plan.changes.size} unresolved=${plan.unresolved}")
        migrated.set(plan.complete)
    }

    internal suspend fun <T> withRestoreSources(
        includeExtensions: Boolean,
        restore: suspend (List<InstalledSourceIdentity>) -> T,
    ): T = migrationMutex.withLock {
        // Serialize startup migration and restore so their completion markers cannot race.
        restore(if (includeExtensions) scanSources() else emptyList())
    }

    private suspend fun scanSources(): List<InstalledSourceIdentity> {
        // awaitReady() swallows an initial scan failure; refresh() must succeed here.
        extensionManager.refresh()
        return extensionManager.extensions.value.map {
            InstalledSourceIdentity(it.id, it.source?.javaClass?.getAnnotation(SourceInfo::class.java)?.baseUrl)
        }
    }
}
