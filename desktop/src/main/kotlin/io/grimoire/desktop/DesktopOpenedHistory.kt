package io.grimoire.desktop

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.storage.OpenedHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DesktopOpenedChapter(val key: ChapterKey, val novelTitle: String, val chapterTitle: String)
data class DesktopHistoryEntry(val id: Long, val chapter: DesktopOpenedChapter, val openedAtMillis: Long)

internal class DesktopOpenedHistoryDao(private val library: LocalLibrary) : OpenedHistoryDao<DesktopOpenedChapter, Long> {
    override suspend fun record(snapshot: DesktopOpenedChapter, openedAtMillis: Long) = withContext(Dispatchers.IO) {
        library.recordOpenedChapter(snapshot, openedAtMillis)
    }

    override suspend fun delete(recordIds: List<Long>) = withContext(Dispatchers.IO) {
        library.deleteOpenedHistory(recordIds)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { library.clearOpenedHistory() }
}
