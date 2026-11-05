package io.grimoire.app.data.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class TtsSynthesisRequestsTest {
    @Test fun speakReusesUnfinishedPrefetch() = runBlocking {
        val finish = CompletableDeferred<ByteArray>()
        val requests = TtsSynthesisRequests(this)
        var posts = 0
        val prefetch = requests.get("paragraph") { posts++; finish.await() }
        yield()
        val speak = requests.get("paragraph") { posts++; byteArrayOf(2) }
        assertSame(prefetch, speak)
        finish.complete(byteArrayOf(1))
        assertArrayEquals(byteArrayOf(1), speak.await())
        assertEquals(1, posts)
    }

    @Test fun stopCancelsAndDiscardsPendingPrefetch() = runBlocking {
        val requests = TtsSynthesisRequests(this)
        val finish = CompletableDeferred<ByteArray>()
        val old = requests.get("paragraph") { finish.await() }
        yield()
        requests.clear()
        finish.complete(byteArrayOf(1))
        val next = requests.get("paragraph") { byteArrayOf(2) }
        assertTrue(old.isCancelled)
        assertNotSame(old, next)
        assertArrayEquals(byteArrayOf(2), next.await())
    }

    @Test fun pausedSynthesisWaitsForResumeAndPreparation() {
        val playback = TtsAudioPlayback()
        playback.pause()
        assertFalse(playback.prepared())
        assertEquals(TtsAudioPlayback.Resume.START, playback.resume())
        playback.reset()
        playback.pause()
        assertEquals(TtsAudioPlayback.Resume.WAIT, playback.resume())
        assertTrue(playback.prepared())
    }

    @Test fun completionCannotAdvanceUntilResumedAndStopDiscardsIt() {
        val playback = TtsAudioPlayback()
        playback.prepared()
        playback.pause()
        assertFalse(playback.completed())
        assertEquals(TtsAudioPlayback.Resume.COMPLETE, playback.resume())
        assertEquals(TtsAudioPlayback.Resume.WAIT, playback.resume())
        playback.pause()
        playback.completed()
        playback.reset()
        assertEquals(TtsAudioPlayback.Resume.WAIT, playback.resume())
    }
}
