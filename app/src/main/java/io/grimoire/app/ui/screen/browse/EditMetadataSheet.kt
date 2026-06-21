package io.grimoire.app.ui.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelStatus
import io.grimoire.app.ui.component.displayName

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
            EditableField.GENRES -> overrides.copy(genres = parseGenres(text).takeIf { it != source.genres })
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
