package io.grimoire.app.ui.screen.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import io.grimoire.reading.storage.ReadingStateSession

internal class ReaderScrollInput(
    private val onUserInput: () -> ReadingStateSession?,
    private val onConsumedInput: (ReadingStateSession) -> Unit,
) : NestedScrollConnection {
    private var inputWriter: ReadingStateSession? = null
    private var lastConsumedWriter: ReadingStateSession? = null
    private var flingWriter: ReadingStateSession? = null

    fun resetVisualInput() {
        inputWriter = null
        lastConsumedWriter = null
        flingWriter = null
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            inputWriter = if (available.y != 0f) onUserInput() else null
            flingWriter = null
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val writer = if (source == NestedScrollSource.UserInput) {
            val captured = inputWriter
            inputWriter = null
            if (consumed.y != 0f) lastConsumedWriter = captured
            captured
        } else flingWriter
        if (consumed.y != 0f && writer != null) onConsumedInput(writer)
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // A fling keeps the original writer even if an explicit intent rebinds the reader.
        flingWriter = lastConsumedWriter
        lastConsumedWriter = null
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        resetVisualInput()
        return Velocity.Zero
    }
}
