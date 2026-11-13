package io.grimoire.app.data.download

import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class DownloadRequestWriterTest {
    @Test
    fun rejectedLocalEnqueueAndRetriesKeepPausedBacklogUntouched() = runBlocking {
        val fixture = Fixture()
        val local = fixture.rows.getValue(2)
        fixture.writer.enqueue(listOf(local), force = true, startService = true)
        fixture.writer.retryChapter(local.copy(downloadStatus = ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal))
        fixture.writer.retryAll(local.novelId)
        assertTrue(fixture.paused)
        assertNull(fixture.serviceDrain())
        assertEquals(ChapterDownloadStatus.QUEUED.ordinal, fixture.rows.getValue(1).downloadStatus)
        assertEquals(ChapterDownloadStatus.DOWNLOADED.ordinal, fixture.rows.getValue(2).downloadStatus)
    }

    @Test
    fun cancelledQueuedSnapshotCannotAuthorizeUnrelatedBacklog() = runBlocking {
        val fixture = Fixture()
        val stale = fixture.rows.getValue(3).copy(downloadStatus = ChapterDownloadStatus.QUEUED.ordinal)
        fixture.writer.enqueue(listOf(stale), force = false, startService = true)
        assertTrue(fixture.paused)
        assertNull(fixture.serviceDrain())
        assertEquals(ChapterDownloadStatus.NONE.ordinal, fixture.rows.getValue(3).downloadStatus)
        assertEquals(ChapterDownloadStatus.QUEUED.ordinal, fixture.rows.getValue(1).downloadStatus)
    }

    @Test
    fun cancelledErrorSnapshotCannotAuthorizeRetry() = runBlocking {
        val fixture = Fixture()
        fixture.writer.retryChapter(fixture.rows.getValue(3).copy(downloadStatus = ChapterDownloadStatus.ERROR.ordinal))
        assertTrue(fixture.paused)
        assertNull(fixture.serviceDrain())
    }

    @Test
    fun completedRowCannotBeDowngradedByStaleFreshRequest() = runBlocking {
        val fixture = Fixture()
        val stale = fixture.rows.getValue(3)
        fixture.rows[3] = stale.copy(downloadStatus = ChapterDownloadStatus.DOWNLOADED.ordinal)
        fixture.writer.enqueue(listOf(stale), force = false, startService = true)
        assertEquals(ChapterDownloadStatus.DOWNLOADED.ordinal, fixture.rows.getValue(3).downloadStatus)
        assertTrue(fixture.paused)
        assertNull(fixture.serviceDrain())
    }

    @Test
    fun acceptedWebRequestPersistsThenResumesQueue() = runBlocking {
        val fixture = Fixture()
        fixture.writer.enqueue(listOf(fixture.rows.getValue(3)), force = false, startService = true)
        assertFalse(fixture.paused)
        assertEquals(listOf(1L, 3L), fixture.serviceDrain())
        assertNull(fixture.serviceDrain())
    }

    @Test
    fun largeBatchLeavesRoomForSqlStatusParameters() = runBlocking {
        val fixture = Fixture()
        val chapters = (1_000L until 3_000L).map { chapter(it, ChapterDownloadStatus.NONE) }
        chapters.forEach { fixture.rows[it.id] = it }
        fixture.writer.enqueue(chapters, force = false, startService = true)
        assertTrue(fixture.writeBindCounts.all { it <= 999 })
        assertTrue(chapters.all { fixture.rows.getValue(it.id).downloadStatus == ChapterDownloadStatus.QUEUED.ordinal })
        assertEquals(2_001, fixture.serviceDrain()?.size)
    }

    @Test
    fun inlineWorkerEnqueueKeepsPauseAndDoesNotAuthorizeService() = runBlocking {
        val fixture = Fixture()
        fixture.writer.enqueue(listOf(fixture.rows.getValue(3)), force = false, startService = false)
        assertTrue(fixture.paused)
        assertEquals(ChapterDownloadStatus.QUEUED.ordinal, fixture.rows.getValue(3).downloadStatus)
        assertNull(fixture.serviceDrain())
    }

    private class Fixture {
        var paused = true
        val requests = DownloadServiceRequests()
        val writeBindCounts = mutableListOf<Int>()
        val rows = linkedMapOf(
            1L to chapter(1, ChapterDownloadStatus.QUEUED),
            2L to chapter(2, ChapterDownloadStatus.DOWNLOADED),
            3L to chapter(3, ChapterDownloadStatus.NONE),
        )
        private val chapterDao = dao<ChapterDao> { method, args ->
            when (method) {
                "queueRequestedDownloads" -> {
                    @Suppress("UNCHECKED_CAST")
                    val ids = args[0] as List<Long>
                    @Suppress("UNCHECKED_CAST")
                    val previous = args[2] as List<Int>
                    writeBindCounts += ids.size + previous.size + 1
                    ids.count { id ->
                        val chapter = rows[id]
                        if (chapter == null || chapter.locked || chapter.downloadStatus !in previous) false else {
                            rows[id] = chapter.copy(downloadStatus = args[1] as Int)
                            true
                        }
                    }
                }
                "countQueuedDownloads" -> {
                    @Suppress("UNCHECKED_CAST")
                    val ids = args[0] as List<Long>
                    ids.count { id -> rows[id]?.let { !it.locked && it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS } == true }
                }
                "retryDownload" -> retry(args[0] as Long)
                "retryAllFailed" -> rows.values.filter { it.novelId == args[0] }.sumOf { retry(it.id) }
                else -> error("Unexpected DAO call: $method")
            }
        }
        private val novelDao = dao<NovelDao> { method, args ->
            check(method == "getById") { "Unexpected novel DAO call: $method" }
            val id = args[0] as Long
            NovelEntity(id = id, sourceId = if (id == 2L) LOCAL_SOURCE_ID else 100L, url = "novel-$id", title = "Novel $id")
        }
        val writer = DownloadRequestWriter(chapterDao, novelDao) {
            paused = false
            requests.allow()
        }

        suspend fun serviceDrain(): List<Long>? = requests.run({}) {
            rows.values.filter { it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS }.map { it.id }
        }

        private fun retry(id: Long): Int {
            val chapter = rows[id] ?: return 0
            val status = when (chapter.downloadStatus) {
                ChapterDownloadStatus.ERROR.ordinal -> ChapterDownloadStatus.QUEUED.ordinal
                ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal -> ChapterDownloadStatus.REDOWNLOAD_QUEUED.ordinal
                else -> return 0
            }
            rows[id] = chapter.copy(downloadStatus = status)
            return 1
        }
    }

    companion object {
        private fun chapter(id: Long, status: ChapterDownloadStatus) = ChapterEntity(
            id = id, novelId = id, url = "chapter-$id", name = "Chapter $id", downloadStatus = status.ordinal,
        )

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> dao(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                invoke(method.name, args.orEmpty())
            } as T
    }
}
