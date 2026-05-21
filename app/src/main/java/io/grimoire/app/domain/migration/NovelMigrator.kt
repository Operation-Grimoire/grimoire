package io.grimoire.app.domain.migration

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
 * Chapters are paired by chapter number, falling back to a number parsed out of
 * the chapter name and then to an exact name match, so progress still carries
 * over for sources that don't expose chapter numbers.
 */
class NovelMigrator @Inject constructor(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
) {

    /**
     * Returns [toNovelId]'s chapters that would be marked read by migrating
     * [fromNovelId] onto it, with the migrated read fields already applied.
     * Used to preview the outcome before committing.
     */
    suspend fun matchReadProgress(fromNovelId: Long, toNovelId: Long): List<ChapterEntity> {
        val source = chapterDao.getChaptersOnce(fromNovelId)
        val target = chapterDao.getChaptersOnce(toNovelId)
        return matchReadProgress(source, target)
    }

    /**
     * Copies read progress from [fromNovelId] onto [toNovelId], saves the target
     * to the library, and removes the original from it (its history is kept).
     */
    suspend fun migrate(fromNovelId: Long, toNovelId: Long) {
        val source = novelDao.getById(fromNovelId) ?: error("Original novel not found")
        val target = novelDao.getById(toNovelId) ?: error("Target novel not found")
        if (source.id == target.id) error("Cannot migrate a novel onto itself")

        val migratedChapters = matchReadProgress(fromNovelId, toNovelId)
        if (migratedChapters.isNotEmpty()) chapterDao.upsertAll(migratedChapters)

        // Save the target to the library, carrying over library metadata.
        novelDao.upsert(
            target.copy(
                favorite = true,
                categoryId = source.categoryId,
                chapterSortOrder = source.chapterSortOrder,
                lastReadAt = source.lastReadAt,
            ),
        )
        // Drop the original from the library; its history stays in the DB.
        novelDao.upsert(source.copy(favorite = false))
    }

    private fun matchReadProgress(
        source: List<ChapterEntity>,
        target: List<ChapterEntity>,
    ): List<ChapterEntity> {
        val readSource = source.filter { it.read || it.readProgress > 0f }
        if (readSource.isEmpty()) return emptyList()

        val byNumber = HashMap<Float, ChapterEntity>()
        val byName = HashMap<String, ChapterEntity>()
        for (chapter in readSource) {
            val number = chapter.effectiveNumber()
            if (number > 0f) byNumber.putIfAbsent(number, chapter)
            byName.putIfAbsent(chapter.name.chapterNameKey(), chapter)
        }

        return target.mapNotNull { chapter ->
            val match = byNumber[chapter.effectiveNumber()]
                ?: byName[chapter.name.chapterNameKey()]
                ?: return@mapNotNull null
            chapter.copy(
                read = match.read,
                readProgress = match.readProgress,
                firstReadAt = chapter.firstReadAt ?: match.firstReadAt,
            )
        }
    }
}

private val CHAPTER_NUMBER = Regex(
    """\b(?:chapter|chap|episode|part)\b\.?\s*(\d+(?:\.\d+)?)""",
    RegexOption.IGNORE_CASE,
)
private val ANY_NUMBER = Regex("""\d+(?:\.\d+)?""")
private val WHITESPACE = Regex("""\s+""")

/** The chapter's own number, or one parsed from its name when it has none. */
private fun ChapterEntity.effectiveNumber(): Float {
    if (chapterNumber > 0f) return chapterNumber
    CHAPTER_NUMBER.find(name)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
    return ANY_NUMBER.find(name)?.value?.toFloatOrNull() ?: -1f
}

private fun String.chapterNameKey(): String =
    trim().lowercase().replace(WHITESPACE, " ")
