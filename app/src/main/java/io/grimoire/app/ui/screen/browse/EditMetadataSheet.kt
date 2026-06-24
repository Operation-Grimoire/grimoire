package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.app.ui.component.displayName
import sh.calvin.reorderable.ReorderableColumn

/** A single novel-metadata field that can be overridden (#152). */
internal enum class EditableField(val label: String) {
    TITLE("title"),
    AUTHOR("author"),
    STATUS("status"),
    GENRES("genres"),
    DESCRIPTION("description"),
}

/**
 * Edits ONE metadata field at a time in a focused bottom sheet (opened by long-pressing
 * the field, or tapping its override indicator). "Reset to source" clears the override
 * for this field; saving a value equal to the source also clears it. Other fields and the
 * cover override are passed through untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetadataFieldEditSheet(
    field: EditableField,
    source: Novel,
    overrides: NovelOverrides,
    onSave: (NovelOverrides) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var text by remember {
            mutableStateOf(
                when (field) {
                    EditableField.TITLE -> overrides.title ?: source.title
                    EditableField.AUTHOR -> overrides.author ?: source.author.orEmpty()
                    EditableField.DESCRIPTION -> overrides.description ?: source.description.orEmpty()
                    EditableField.GENRES -> (overrides.genres ?: source.genres).joinToString(", ")
                    EditableField.STATUS -> ""
                },
            )
        }
        var status by remember { mutableStateOf(overrides.status ?: source.status) }
        var statusMenu by remember { mutableStateOf(false) }
        val genreList = remember { mutableStateListOf<String>().apply { addAll(overrides.genres ?: source.genres) } }

        val isOverridden = when (field) {
            EditableField.TITLE -> overrides.title != null
            EditableField.AUTHOR -> overrides.author != null
            EditableField.DESCRIPTION -> overrides.description != null
            EditableField.GENRES -> overrides.genres != null
            EditableField.STATUS -> overrides.status != null
        }

        fun overridesWithEdit(): NovelOverrides = when (field) {
            EditableField.TITLE -> overrides.copy(title = text.trim().takeIf { it != source.title })
            EditableField.AUTHOR -> overrides.copy(author = text.trim().takeIf { it != source.author.orEmpty() })
            EditableField.DESCRIPTION -> overrides.copy(description = text.trim().takeIf { it != source.description.orEmpty() })
            EditableField.GENRES -> overrides.copy(genres = genreList.toList().takeIf { it != source.genres })
            EditableField.STATUS -> overrides.copy(status = status.takeIf { it != source.status })
        }

        fun overridesWithReset(): NovelOverrides = when (field) {
            EditableField.TITLE -> overrides.copy(title = null)
            EditableField.AUTHOR -> overrides.copy(author = null)
            EditableField.DESCRIPTION -> overrides.copy(description = null)
            EditableField.GENRES -> overrides.copy(genres = null)
            EditableField.STATUS -> overrides.copy(status = null)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Edit ${field.label}", style = MaterialTheme.typography.titleLarge)

            when (field) {
                EditableField.STATUS -> Box {
                    OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(status.displayName, modifier = Modifier.weight(1f))
                        Icon(AppIcons.ArrowDropDown, contentDescription = null)
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
                EditableField.GENRES -> GenreListEditor(genreList)
                else -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(field.label.replaceFirstChar { it.uppercase() }) },
                    singleLine = field == EditableField.TITLE || field == EditableField.AUTHOR,
                    minLines = if (field == EditableField.DESCRIPTION) 4 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(
                    enabled = isOverridden,
                    onClick = { onSave(overridesWithReset()); onDismiss() },
                ) { Text("Reset to source") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(overridesWithEdit()); onDismiss() }) { Text("Save") }
            }
        }
    }
}

private fun parseGenres(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Editable genre list: add via the text field (comma-separated input is split),
 * remove with the trailing X, and drag-reorder via the handle (sh.calvin.reorderable
 * [ReorderableColumn], suited to this short non-lazy list inside a scrolling sheet).
 * Operates directly on the [genres] snapshot list owned by the sheet.
 */
@Composable
private fun GenreListEditor(genres: SnapshotStateList<String>) {
    var newGenre by remember { mutableStateOf("") }

    fun addGenres() {
        parseGenres(newGenre).forEach { g ->
            if (genres.none { it.equals(g, ignoreCase = true) }) genres.add(g)
        }
        newGenre = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (genres.isEmpty()) {
            Text(
                "No genres",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReorderableColumn(
            list = genres.toList(),
            onSettle = { from, to -> genres.add(to, genres.removeAt(from)) },
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { _, genre, isDragging ->
            key(genre) {
                ReorderableItem {
                    Surface(
                        tonalElevation = if (isDragging) 4.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle()
                                    .size(40.dp)
                                    .padding(8.dp),
                            )
                            Text(genre, modifier = Modifier.weight(1f))
                            IconButton(onClick = { genres.remove(genre) }) {
                                Icon(AppIcons.Close, contentDescription = "Remove $genre")
                            }
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newGenre,
                onValueChange = { newGenre = it },
                label = { Text("Add genre") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addGenres() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { addGenres() }) {
                Icon(AppIcons.Add, contentDescription = "Add genre")
            }
        }
    }
}
