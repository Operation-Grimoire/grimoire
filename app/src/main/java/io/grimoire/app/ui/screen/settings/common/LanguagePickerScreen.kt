package io.grimoire.app.ui.screen.settings.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.app.util.ContentLanguages

/**
 * State holder for the optional "Override global" toggle shown at the top of
 * the per-source picker. Pass `null` from the global picker.
 */
data class OverrideToggle(
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit,
    val label: String = "Override global content languages",
    val helperWhenOn: String = "Pick the languages you want for this source only.",
    val helperWhenOff: String =
        "This source uses the global content-language selection. " +
            "Turn this on to pick a different mix just for this source.",
)

/**
 * Diff badge shown on each row in the per-source picker so the user can see
 * which languages match the global pick, which are added just here, and which
 * are explicitly hidden here vs. the global.
 *
 * `null` in the global picker (no diff to show).
 */
sealed interface LanguageDiff {
    /** Checked here AND in the global set — matches global. */
    data object MatchesGlobal : LanguageDiff
    /** Checked here but NOT in the global set — added just for this source. */
    data object AddedHere : LanguageDiff
    /** Unchecked here but IN the global set — hidden just for this source. */
    data object HiddenHere : LanguageDiff
    /** Unchecked and not in global — plain row, no badge. */
    data object None : LanguageDiff
}

/**
 * The single picker UI shared by the global content-language picker and the
 * per-source override picker. Extensions never render their own picker — they
 * just declare `availableLanguages()` (data) and the host shows this.
 *
 * @param available languages to render as rows (already in the order to show).
 * @param enabled current selection (compared case-insensitively, lowercase).
 * @param onToggle invoked with the language name when a row is tapped.
 * @param onSave invoked when the user taps "Save".
 * @param overrideToggle if non-null, an extra toggle is shown above the list.
 *   When the toggle is off, the list rows are disabled.
 * @param globalSet the global selection — only used to compute the per-source
 *   diff badges. Pass `null` from the global picker.
 * @param helper top-of-screen helper text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerScreen(
    title: String,
    available: List<String>,
    enabled: Set<String>,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    saved: Boolean,
    onNavigateBack: () -> Unit,
    overrideToggle: OverrideToggle? = null,
    globalSet: Set<String>? = null,
    helper: String? = null,
    modifier: Modifier = Modifier,
) {
    val showDiff = globalSet != null && (overrideToggle?.enabled ?: true)
    val rowsEnabled = overrideToggle?.enabled ?: true

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(title) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (helper != null) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (overrideToggle != null) {
                ListItem(
                    headlineContent = { Text(overrideToggle.label) },
                    supportingContent = {
                        Text(
                            if (overrideToggle.enabled) overrideToggle.helperWhenOn
                            else overrideToggle.helperWhenOff,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = overrideToggle.enabled,
                            onCheckedChange = overrideToggle.onToggle,
                        )
                    },
                )
                HorizontalDivider()
            }

            if (!rowsEnabled && globalSet != null) {
                val summary = when {
                    globalSet.isEmpty() -> "Global: no filter (all languages shown)"
                    else -> "Global: ${globalSet.size} language" +
                        (if (globalSet.size == 1) "" else "s") + " selected"
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(bottom = 0.dp)) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(available, key = { it }) { lang ->
                        val key = ContentLanguages.normalize(lang)
                        val checkedHere = key in enabled
                        val inGlobal = globalSet?.let { key in it } ?: false
                        val diff = when {
                            !showDiff -> LanguageDiff.None
                            checkedHere && inGlobal -> LanguageDiff.MatchesGlobal
                            checkedHere && !inGlobal -> LanguageDiff.AddedHere
                            !checkedHere && inGlobal -> LanguageDiff.HiddenHere
                            else -> LanguageDiff.None
                        }
                        LanguageRow(
                            name = lang,
                            checked = checkedHere,
                            enabled = rowsEnabled,
                            diff = diff,
                            onClick = { onToggle(lang) },
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(if (saved) "Saved" else "Save")
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    name: String,
    checked: Boolean,
    enabled: Boolean,
    diff: LanguageDiff,
    onClick: () -> Unit,
) {
    val dimText = !enabled || diff == LanguageDiff.HiddenHere
    val headlineColor = if (dimText) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        headlineContent = { Text(name, color = headlineColor) },
        leadingContent = {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DiffChip(diff)
            }
        },
        colors = ListItemDefaults.colors(),
        modifier = Modifier.fillMaxWidth().then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    )
}

@Composable
private fun DiffChip(diff: LanguageDiff) {
    when (diff) {
        LanguageDiff.None -> Spacer(Modifier.height(0.dp))
        LanguageDiff.MatchesGlobal -> AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Global") },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        LanguageDiff.AddedHere -> AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Added here") },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        LanguageDiff.HiddenHere -> AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Hidden here") },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
