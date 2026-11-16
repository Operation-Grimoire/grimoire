package io.grimoire.app.ui.component.sheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The app's replacement for rows of filter chips: a full-width segmented
 * button row. Use for an exclusive choice between up to ~5 options; larger or
 * multi-select sets belong in dropdowns, checkbox lists, or tri-state rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSelectedIcon: Boolean = options.size <= 4,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    if (showSelectedIcon) {
                        SegmentedButtonDefaults.Icon(isSelected)
                    }
                },
                label = {
                    Text(
                        text = label(option),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Multi-select variant — each segment toggles independently. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiChoiceSegmented(
    options: List<T>,
    isChecked: (T) -> Boolean,
    onToggle: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    isEnabled: (T) -> Boolean = { true },
) {
    MultiChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                checked = isChecked(option),
                onCheckedChange = { onToggle(option) },
                enabled = isEnabled(option),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}
