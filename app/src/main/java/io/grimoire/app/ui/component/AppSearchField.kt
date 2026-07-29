package io.grimoire.app.ui.component

import io.grimoire.app.R
import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.fillMaxWidth
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
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
    showLeadingIcon: Boolean = true,
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
        leadingIcon = if (showLeadingIcon) {
            { Icon(AppIcons.Search, contentDescription = null) }
        } else null,
        trailingIcon = {
            if (value.isNotEmpty()) {
                val clearLabel = stringResource(R.string.action_clear_search)
                PlainTooltipIconButton(onClick = { onValueChange("") }, tooltip = clearLabel) {
                    Icon(AppIcons.Clear, contentDescription = clearLabel)
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
