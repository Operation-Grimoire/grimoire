package io.grimoire.reading.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** A platform owns its presentation snapshot, record identity, and physical storage. */
interface OpenedHistoryDao<Snapshot : Any, RecordId : Any> {
    suspend fun record(snapshot: Snapshot, openedAtMillis: Long)
    suspend fun delete(recordIds: List<RecordId>)
    suspend fun clear()
}

/**
 * Order every open, selected delete and clear in one store owner's scope. An open's resolver
 * runs here, after its privacy choice and time have been accepted. It should only resolve local
 * metadata. A failed action is never retried implicitly or allowed to block later actions.
 */
class OpenedHistoryWrites<Snapshot : Any, RecordId : Any>(
    scope: CoroutineScope,
    private val dao: OpenedHistoryDao<Snapshot, RecordId>,
    private val onFailure: (Exception) -> Unit = {},
) {
    private class Pending(
        val write: suspend () -> Unit,
        val result: CompletableDeferred<Result<Unit>> = CompletableDeferred(),
    )

    private val queue = Channel<Pending>(Channel.UNLIMITED, onUndeliveredElement = { it.result.cancel() })

    init {
        val worker = scope.launch {
            for (pending in queue) {
                try {
                    currentCoroutineContext().ensureActive()
                    pending.write()
                    pending.result.complete(Result.success(Unit))
                } catch (cancelled: CancellationException) {
                    pending.result.cancel(cancelled)
                    currentCoroutineContext().ensureActive()
                } catch (failure: Exception) {
                    pending.result.complete(Result.failure(failure))
                    onFailure(failure)
                }
            }
        }
        worker.invokeOnCompletion { queue.cancel() }
    }

    fun open(incognito: Boolean, openedAtMillis: Long, resolve: suspend () -> Snapshot?): Deferred<Result<Unit>>? {
        if (incognito) return null
        return submit { resolve()?.let { dao.record(it, openedAtMillis) } }
    }

    fun delete(recordIds: Collection<RecordId>): Deferred<Result<Unit>>? {
        if (recordIds.isEmpty()) return null
        val captured = recordIds.distinct()
        return submit { dao.delete(captured) }
    }

    fun clear(): Deferred<Result<Unit>> = submit { dao.clear() }

    private fun submit(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val pending = Pending(write)
        if (queue.trySend(pending).isFailure) pending.result.cancel()
        return pending.result
    }
}
