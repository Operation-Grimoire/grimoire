package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.ui.component.displayName

/**
 * Edits per-field metadata overrides (#152). Each field is seeded with its current
 * effective value (override ?: source). On save, a field equal to the source value is
 * stored as `null` (no override) — that's how "reset to source" works: clear the field
 * back to the source text and the source value reappears on the next refresh.
 *
 * Cover overrides are managed from the cover viewer, not here, so the emitted
 * [NovelOverrides] leaves [NovelOverrides.coverPath] / [NovelOverrides.coverUrl] null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditMetadataSheet(
    source: Novel,
    overrides: NovelOverrides,
    onSave: (NovelOverrides) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var title by remember { mutableStateOf(overrides.title ?: source.title) }
        var author by remember { mutableStateOf(overrides.author ?: source.author.orEmpty()) }
        var description by remember { mutableStateOf(overrides.description ?: source.description.orEmpty()) }
        var status by remember { mutableStateOf(overrides.status ?: source.status) }
        var genres by remember { mutableStateOf((overrides.genres ?: source.genres).joinToString(", ")) }
        var statusMenu by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit metadata", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Box {
                OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Status: ${status.displayName}", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    NovelStatus.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = { status = option; statusMenu = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = genres,
                onValueChange = { genres = it },
                label = { Text("Genres (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    onSave(
                        NovelOverrides(
                            title = title.trim().takeIf { it != source.title },
                            author = author.trim().takeIf { it != source.author.orEmpty() },
                            description = description.trim().takeIf { it != source.description.orEmpty() },
                            status = status.takeIf { it != source.status },
                            genres = parseGenres(genres).takeIf { it != source.genres },
                            coverPath = overrides.coverPath,
                            coverUrl = overrides.coverUrl,
                        ),
                    )
                    onDismiss()
                }) { Text("Save") }
            }
        }
    }
}

private fun parseGenres(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
