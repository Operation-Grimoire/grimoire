package io.grimoire.app.ui.component.dialog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.grimoire.app.R
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.ui.icon.*

/**
 * M3 full-screen dialog: close (X) left, title, optional confirm action right.
 * The home for form-shaped surfaces — filter forms, editors — that used to
 * live in scrollable bottom sheets. Dismissing (X, back, outside) is an
 * explicit cancel; only the confirm action commits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            PlainTooltipIconButton(
                                onClick = onDismiss,
                                tooltip = stringResource(R.string.action_close),
                            ) {
                                Icon(
                                    AppIcons.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                )
                            }
                        },
                        title = { Text(title) },
                        actions = {
                            actions()
                            confirmLabel?.let {
                                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                                    Text(it)
                                }
                            }
                        },
                    )
                },
            ) { padding -> content(padding) }
        }
    }
}
