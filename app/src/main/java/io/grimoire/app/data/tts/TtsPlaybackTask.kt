package io.grimoire.app.data.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Only the latest playback operation may change playback after a suspension. */
internal class TtsPlaybackTask(
    private val scope: CoroutineScope,
    private val onFailure: (Exception) -> Unit,
) {
    private var job: Job? = null

    fun cancel() { job?.cancel() }

    fun launch(block: suspend () -> Unit) {
        cancel()
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                onFailure(e)
            }
        }
    }
}

internal fun chapterStartUtterance(read: Boolean, progress: Float, count: Int, startIndex: Int?): Int {
    if (count <= 0) return 0
    if (startIndex != null) return startIndex.coerceIn(0, count - 1)
    if (read || progress <= 0.01f || progress >= 0.97f) return 0
    return (progress * count).toInt().coerceIn(0, count - 1)
}
