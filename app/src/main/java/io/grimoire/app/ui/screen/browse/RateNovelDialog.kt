package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Picker for the user's own 1–10 rating. A slider selects the value; the selection is held
 * locally and committed on Save, while Clear removes the rating entirely. Distinct from the
 * source rating shown in the header.
 */
@Composable
internal fun RateNovelDialog(
    current: Int?,
    onSetRating: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Slider always carries a value; unrated novels open at the midpoint. Clear (not the
    // slider) is what removes a rating.
    var value by remember { mutableFloatStateOf((current ?: 5).toFloat()) }
    val rating = value.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your rating") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$rating / 10",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 1f..10f,
                    steps = 8, // 10 discrete stops (8 interior + both ends)
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Your own score, separate from the source rating.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = rating != current,
                onClick = { onSetRating(rating); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onSetRating(null); onDismiss() }) { Text("Clear") }
        },
    )
}
