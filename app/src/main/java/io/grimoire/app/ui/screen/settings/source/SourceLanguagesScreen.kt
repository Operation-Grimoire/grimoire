package io.grimoire.app.ui.screen.settings.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.common.LanguagePickerScreen
import io.grimoire.app.ui.screen.settings.common.OverrideToggle
import io.grimoire.app.R

@Composable
fun SourceLanguagesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceLanguagesViewModel = hiltViewModel(),
) {
    val override by viewModel.override.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val globalSet by viewModel.globalSet.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val available by viewModel.available.collectAsState()

    // When override is off, render the global selection on the rows so the
    // user sees exactly which languages are currently active for this source.
    val rowSelection = if (override) enabled else globalSet

    val toggle = remember(viewModel) { { v: Boolean -> viewModel.setOverride(v) } }

    LanguagePickerScreen(
        title = stringResource(R.string.source_settings_languages_title, viewModel.sourceName),
        available = available,
        enabled = rowSelection,
        onToggle = viewModel::toggle,
        onSave = viewModel::save,
        saved = saved,
        onNavigateBack = onNavigateBack,
        overrideToggle = OverrideToggle(
            enabled = override,
            onToggle = toggle,
        ),
        globalSet = globalSet,
        helper = null,
        modifier = modifier,
    )
}
