package io.grimoire.app.extension

import kotlinx.coroutines.CancellationException

/** Isolate third-party failures while preserving coroutine cancellation. */
internal suspend fun catchExtensionFailure(block: suspend () -> Unit): Result<Unit> =
    runCatching { block() }.onFailure {
        if (it is CancellationException) throw it
    }
