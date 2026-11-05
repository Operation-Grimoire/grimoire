package io.grimoire.app.domain.migration

import androidx.room.withTransaction
import io.grimoire.app.data.cover.CustomCoverStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import javax.inject.Inject

/** Progress of an in-flight or completed migration. */
sealed interface MigrationState {
    data object Idle : MigrationState
    data object Running : MigrationState
    data object Success : MigrationState
    data class Error(val message: String) : MigrationState
}

/**
 * Moves a user's read progress from one library novel onto another — typically
 * the same novel on a different source.
 *
 * Both novels and their chapters are already persisted before a migration runs,
 * so this only updates existing rows (never inserts), sidestepping the foreign
 * key pitfalls of relying on an `@Upsert` row id.
 *
 * Chapters are paired by unique normalized names first, then by unambiguous
 * chapter numbers, so repeated per-volume numbers cannot leak read state.
 */
class NovelMigrator @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val database: AppDatabase,
    private val coverStore: CustomCoverStore,
) {

    /**
     * Returns [toNovelId]'s chapters that would be marked read by migrating
     * [fromNovelId] onto it, with the migrated read fields already applied.
     * Used to preview the outcome before committing.
     */
    suspend fun matchReadProgress(fromNovelId: Long, toNovelId: Long): List<ChapterEntity> {
        val source = chapterDao.getChaptersOnce(fromNovelId)
        val target = chapterDao.getChaptersOnce(toNovelId)
        return matchMigratedChapters(source, target)
    }

    /**
     * Copies read progress from [fromNovelId] onto [toNovelId], saves the target
     * to the library, and removes the original from it (its history is kept).
     */
    suspend fun migrate(fromNovelId: Long, toNovelId: Long) {
        var copiedCover: File? = null
        try {
            database.withTransaction {
                val source = novelDao.getById(fromNovelId) ?: error("Original novel not found")
                val target = novelDao.getById(toNovelId) ?: error("Target novel not found")
                if (source.id == target.id) error("Cannot migrate a novel onto itself")

                source.customCoverPath?.let { path ->
                    // Finish staging so cancellation cleanup always knows the new file.
                    withContext(NonCancellable) {
                        copiedCover = coverStore.copyForMigration(target.id, path)
                    }
                }
                val migratedChapters = matchReadProgress(fromNovelId, toNovelId)
                if (migratedChapters.isNotEmpty()) chapterDao.upsertAll(migratedChapters)
                novelDao.upsert(target.withMigratedMetadata(source, copiedCover?.absolutePath))
                // Keep the original's history while moving its library membership.
                novelDao.upsert(source.copy(favorite = false))
            }
        } catch (failure: Throwable) {
            copiedCover?.let { copy ->
                withContext(NonCancellable + Dispatchers.IO) {
                    // Cancellation can arrive after commit. Never delete a referenced cover.
                    runCatching {
                        if (novelDao.getById(toNovelId)?.customCoverPath != copy.absolutePath) copy.delete()
                    }
                }
            }
            throw failure
        }
    }
}
