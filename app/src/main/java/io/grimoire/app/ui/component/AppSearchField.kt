package io.grimoire.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The app-wide search input: a borderless single-line field with a leading
 * search icon and a clear (✕) button that appears once there's text. Used by
 * every search screen so they look and behave the same.
 *
 * @param onSearch invoked when the user submits from the keyboard (the soft
 *   keyboard is dismissed automatically beforehand).
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = "Search…",
    onSearch: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // On (re)composition cursor starts at end of existing text; not saveable so it doesn't survive nav restores.
    var selection by remember { mutableStateOf(TextRange(value.length)) }
    val safeSelection = TextRange(
        selection.start.coerceIn(0, value.length),
        selection.end.coerceIn(0, value.length),
    )
    OutlinedTextField(
        value = TextFieldValue(text = value, selection = safeSelection),
        onValueChange = { newValue ->
            selection = newValue.selection
            if (newValue.text != value) onValueChange(newValue.text)
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onSearch()
        }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
        ),
    )
}
