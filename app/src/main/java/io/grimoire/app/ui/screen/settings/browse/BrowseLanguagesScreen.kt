package io.grimoire.app.ui.screen.settings.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.common.LanguagePickerScreen
import io.grimoire.app.R

@Composable
fun BrowseLanguagesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseLanguagesViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LanguagePickerScreen(
        title = stringResource(R.string.content_languages_title),
        available = viewModel.available,
        enabled = enabled,
        onToggle = viewModel::toggle,
        onSave = viewModel::save,
        saved = saved,
        onNavigateBack = onNavigateBack,
        overrideToggle = null,
        globalSet = null,
        helper = stringResource(R.string.content_languages_helper),
        modifier = modifier,
    )
}
