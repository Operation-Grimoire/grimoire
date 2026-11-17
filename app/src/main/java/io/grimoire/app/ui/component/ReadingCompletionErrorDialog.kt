package io.grimoire.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R
import io.grimoire.app.data.reading.CompletionSaveNotice

@Composable
internal fun ReadingCompletionErrorDialog(notice: CompletionSaveNotice, retry: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(stringResource(when (notice) {
                CompletionSaveNotice.SAVE_FAILED -> R.string.reader_completion_save_error
                CompletionSaveNotice.WORD_COUNT_FAILED -> R.string.reader_completion_partial_error
                CompletionSaveNotice.CHANGED -> R.string.reader_completion_changed_error
            }))
        },
        confirmButton = {
            if (notice != CompletionSaveNotice.CHANGED) TextButton(onClick = retry) {
                Text(stringResource(R.string.action_retry))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
