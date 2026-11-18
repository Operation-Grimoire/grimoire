package io.grimoire.app.data.reading

import android.util.Log
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.ReadingHistoryDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.ReadingHistoryEntity
import io.grimoire.reading.storage.OpenedHistoryDao
import io.grimoire.reading.storage.OpenedHistoryWrites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/** The Android app's single owner of reading-history writes. */
@Singleton
class AndroidOpenedHistory internal constructor(
    private val history: ReadingHistoryDao,
    private val novels: NovelDao,
    scope: CoroutineScope,
) {
    @Inject constructor(history: ReadingHistoryDao, novels: NovelDao) :
        this(history, novels, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private data class Target(val sourcePackage: String, val chapter: ChapterEntity)

    private val writes = OpenedHistoryWrites(scope, object : OpenedHistoryDao<Target, Long> {
        override suspend fun record(snapshot: Target, openedAtMillis: Long) {
            val chapter = snapshot.chapter
            val novel = novels.getById(chapter.novelId) ?: return
            history.refresh(
                ReadingHistoryEntity(
                    sourcePackage = snapshot.sourcePackage,
                    novelId = novel.id,
                    novelUrl = novel.url,
                    novelTitle = novel.title,
                    novelThumbnailUrl = novel.thumbnailUrl,
                    chapterUrl = chapter.url,
                    chapterName = chapter.name,
                    chapterNumber = chapter.chapterNumber,
                    openedAt = openedAtMillis,
                ),
            )
        }

        override suspend fun delete(recordIds: List<Long>) = history.deleteSelected(recordIds)
        override suspend fun clear() = history.clearAll()
    }, onFailure = { Log.w("OpenedHistory", "Reading history action failed (${it.javaClass.simpleName})") })

    val entries = history.getAll()

    fun open(sourcePackage: String, incognito: Boolean, openedAtMillis: Long, chapter: suspend () -> ChapterEntity?) =
        writes.open(incognito, openedAtMillis) { chapter()?.let { Target(sourcePackage, it) } }

    fun delete(ids: Collection<Long>) = writes.delete(ids)
    fun clear() = writes.clear()
}
