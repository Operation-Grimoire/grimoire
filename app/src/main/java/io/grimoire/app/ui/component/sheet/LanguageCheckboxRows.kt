package io.grimoire.app.ui.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R
import io.grimoire.app.util.languageLabel

/**
 * Multi-select language rows over the global content-language set — the one
 * language filter control (Extensions and Browse share it). The leading "All"
 * row is checked while the set is empty (= no filter); checking it clears the
 * selection. With "All" active every language reads as selected.
 */
@Composable
fun LanguageCheckboxRows(
    languages: List<String>,
    enabledCodes: Set<String>,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
) {
    val showAll = enabledCodes.isEmpty()
    ListItem(
        headlineContent = { Text(stringResource(R.string.filter_all)) },
        leadingContent = { Checkbox(checked = showAll, onCheckedChange = null) },
        modifier = Modifier.clickable(enabled = !showAll) { onAll() },
    )
    languages.forEach { code ->
        ListItem(
            headlineContent = { Text(languageLabel(code)) },
            leadingContent = {
                Checkbox(checked = showAll || code in enabledCodes, onCheckedChange = null)
            },
            modifier = Modifier.clickable { onToggle(code) },
        )
    }
}
