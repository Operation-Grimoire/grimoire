package io.grimoire.app.data.reading

import io.grimoire.reading.NovelKey
import io.grimoire.reading.state.NovelReadingCompletionCommit
import io.grimoire.reading.state.NovelReadingCompletionFacade
import io.grimoire.reading.state.NovelReadingCompletionIntent
import io.grimoire.reading.state.NovelReadingCompletionSave
import io.grimoire.reading.state.PreparedReadingCompletionBatch
import io.grimoire.reading.state.ReadingCompletionSelection
import io.grimoire.reading.state.ReadingWordCountOutcome
import io.grimoire.reading.state.SelectedReadingCompletionIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread controller for one screen's accepted bulk action and exact retries. */
internal class BulkReadingCompletion(
    private val scope: CoroutineScope,
    private val facade: NovelReadingCompletionFacade,
) {
    private sealed interface Work {
        data class PrepareNovel(val intent: NovelReadingCompletionIntent) : Work
        data class PrepareSelection(val intent: SelectedReadingCompletionIntent) : Work
        data class Save(
            val batches: List<PreparedReadingCompletionBatch>,
            val index: Int = 0,
            val backfill: NovelReadingCompletionSave? = null,
        ) : Work
    }

    private var pending: Work? = null
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _notice = MutableStateFlow<CompletionSaveNotice?>(null)
    val notice = _notice.asStateFlow()

    fun markNovel(novel: NovelKey, read: Boolean) {
        accept(Work.PrepareNovel(NovelReadingCompletionIntent(novel, read, intentTime())))
    }

    fun markNovels(novelIds: Collection<Long>, read: Boolean) {
        if (novelIds.isEmpty()) return
        val selection = ReadingCompletionSelection.Novels(novelIds.distinct())
        accept(Work.PrepareSelection(SelectedReadingCompletionIntent(selection, read, intentTime())))
    }

    fun markChapters(chapterIds: Collection<Long>, read: Boolean, novelId: Long? = null, chunkSize: Int? = null) {
        if (chapterIds.isEmpty()) return
        val selection = ReadingCompletionSelection.Chapters(chapterIds.distinct(), novelId, chunkSize)
        accept(Work.PrepareSelection(SelectedReadingCompletionIntent(selection, read, intentTime())))
    }

    private fun intentTime(): Long = (System.currentTimeMillis() / 1000) * 1000

    private fun accept(work: Work) {
        if (_notice.value != null || pending != null) return
        pending = work
        submit()
    }

    fun retry() {
        submit()
    }

    fun dismiss() {
        if (_saving.value) return
        pending = null
        _notice.value = null
    }

    private fun submit() {
        if (_saving.value || pending == null) return
        _saving.value = true
        _notice.value = null
        scope.launch {
            try {
                drain()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _notice.value = if (((pending as? Work.Save)?.index ?: 0) > 0)
                    CompletionSaveNotice.PARTIAL_SAVE_FAILED else CompletionSaveNotice.SAVE_FAILED
            } finally {
                _saving.value = false
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            pending = when (val work = pending ?: return) {
                is Work.PrepareNovel -> {
                    val batch = facade.prepare(work.intent)
                    if (batch == null) {
                        _notice.value = CompletionSaveNotice.CHANGED
                        null
                    } else Work.Save(listOf(batch))
                }
                is Work.PrepareSelection -> Work.Save(facade.prepare(work.intent))
                is Work.Save -> saveNext(work)
            }
            if (_notice.value != null) return
        }
    }

    private suspend fun saveNext(work: Work.Save): Work.Save? {
        val batch = work.batches.getOrNull(work.index) ?: return null
        val result = if (work.backfill == null) facade.submit(batch) else facade.retryWordCount(work.backfill)
        return when {
            result.confirmation is NovelReadingCompletionCommit.NotApplied -> {
                _notice.value = if (work.index > 0) CompletionSaveNotice.PARTIAL_CHANGED else CompletionSaveNotice.CHANGED
                null
            }
            result.wordCount is ReadingWordCountOutcome.Failed -> {
                _notice.value = if (work.index + 1 < work.batches.size)
                    CompletionSaveNotice.PARTIAL_WORD_COUNT_FAILED else CompletionSaveNotice.WORD_COUNT_FAILED
                work.copy(backfill = result)
            }
            else -> work.copy(index = work.index + 1, backfill = null)
        }
    }
}
