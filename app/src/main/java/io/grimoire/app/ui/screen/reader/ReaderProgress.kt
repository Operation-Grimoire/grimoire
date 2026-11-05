package io.grimoire.app.ui.screen.reader

import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ReaderPosition(
    val chapterId: Long,
    val fraction: Float,
    val anchorIndex: Int,
    val anchorOffset: Int,
)

internal fun ChapterEntity.withReaderPosition(position: ReaderPosition?): ChapterEntity =
    if (position?.chapterId == id) copy(
        readProgress = position.fraction,
        readAnchorItemIndex = position.anchorIndex,
        readAnchorItemOffset = position.anchorOffset,
    ) else this

internal fun acceptsReaderProgress(currentId: Long?, loadedId: Long?, reportedId: Long): Boolean =
    currentId == reportedId && loadedId == reportedId

internal fun shouldMarkReaderChapterRead(
    alreadyRead: Boolean,
    fraction: Float,
    lastVisibleIndex: Int,
    totalItems: Int,
    strategy: MarkAsReadStrategy,
    threshold: Int,
    paragraphsFromEnd: Int,
): Boolean = !alreadyRead && totalItems > 0 && when (strategy) {
    MarkAsReadStrategy.PERCENT -> fraction >= threshold / 100f
    MarkAsReadStrategy.PARAGRAPHS_FROM_END -> totalItems - 1 - lastVisibleIndex <= paragraphsFromEnd
    MarkAsReadStrategy.AT_END -> lastVisibleIndex >= totalItems - 1
}

/** Called on the main thread; writes are serialized even when a flush overlaps an earlier write. */
internal class ReaderProgressWriter(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 500,
    private val write: suspend (ReaderPosition) -> Unit,
) {
    private var pending: ReaderPosition? = null
    private var timer: Job? = null
    private val writes = Mutex()

    fun submit(position: ReaderPosition) {
        if (pending?.chapterId != position.chapterId) flush()
        pending = position
        timer?.cancel()
        timer = scope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    fun flush(): Job? {
        timer?.cancel()
        timer = null
        val position = pending ?: return null
        pending = null
        return scope.launch { writes.withLock { write(position) } }
    }
}
