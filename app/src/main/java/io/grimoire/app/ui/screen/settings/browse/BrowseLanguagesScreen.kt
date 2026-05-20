package io.grimoire.app.ui.screen.settings.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.common.LanguagePickerScreen

@Composable
fun BrowseLanguagesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseLanguagesViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LanguagePickerScreen(
        title = "Content languages",
        available = viewModel.available,
        enabled = enabled,
        onToggle = viewModel::toggle,
        onSave = viewModel::save,
        saved = saved,
        onNavigateBack = onNavigateBack,
        overrideToggle = null,
        globalSet = null,
        helper = "Languages selected here apply to every multi-language source " +
            "unless overridden in that source's settings. Leave all unchecked " +
            "to show every language.",
        modifier = modifier,
    )
}
