package io.grimoire.app.data.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TtsPlaybackTaskTest {
    @Test fun loadFailureReachesFailureHandler() = runBlocking {
        var failure: Exception? = null
        val task = TtsPlaybackTask(this) { failure = it }
        task.launch { throw IOException("offline") }
        yield()
        assertEquals("offline", failure?.message)
    }

    @Test fun stoppedLoadCannotResumePlaybackOrReportAnError() = runBlocking {
        val loaded = CompletableDeferred<Unit>()
        var played = false
        var failed = false
        val task = TtsPlaybackTask(this) { failed = true }
        task.launch {
            withContext(NonCancellable) { loaded.await() }
            currentCoroutineContext().ensureActive()
            played = true
        }
        yield()
        task.cancel()
        loaded.complete(Unit)
        yield()
        assertFalse(played)
        assertFalse(failed)
    }

    @Test fun newerChapterSupersedesPendingLoad() = runBlocking {
        val loaded = CompletableDeferred<Unit>()
        val spoken = mutableListOf<String>()
        val task = TtsPlaybackTask(this) { throw it }
        task.launch { loaded.await(); spoken += "old" }
        yield()
        task.launch { spoken += "new" }
        loaded.complete(Unit)
        yield()
        assertEquals(listOf("new"), spoken)
    }

    @Test fun explicitRestartIgnoresSavedProgress() {
        assertEquals(0, chapterStartUtterance(false, 0.6f, 10, 0))
        assertEquals(6, chapterStartUtterance(false, 0.6f, 10, null))
        assertEquals(0, chapterStartUtterance(true, 0.6f, 10, null))
        assertEquals(0, chapterStartUtterance(false, 1f, 10, null))
    }
}
