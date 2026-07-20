package io.grimoire.app.ui.screen.settings.languages

import io.grimoire.app.ui.icon.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSourceLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }
    val currentTag = remember { AppLocale.storedTag(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(
                        onClick = onNavigateBack,
                        tooltip = stringResource(R.string.action_back),
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.languages_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.languages_app_language_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.languages_app_language_subtitle))
                    },
                    trailingContent = { Text(languageLabel(currentTag)) },
                    modifier = Modifier.clickable { pickerOpen = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.languages_source_language_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.languages_source_language_subtitle))
                    },
                    trailingContent = {
                        Icon(
                            AppIcons.ArrowForwardIos,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToSourceLanguages),
                )
            }
        }
    }

    if (pickerOpen) {
        AppLanguageDialog(
            selectedTag = currentTag,
            onDismiss = { pickerOpen = false },
            onSelect = { tag ->
                pickerOpen = false
                if (tag != currentTag) {
                    AppLocale.setTag(context, tag)
                    // Recreate the activity so every resource re-resolves in the
                    // newly-chosen language. attachBaseContext picks up the new tag.
                    context.findActivity()?.recreate()
                }
            },
        )
    }
}

@Composable
private fun AppLanguageDialog(
    selectedTag: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_picker_title)) },
        text = {
            Column {
                AppLocale.supported.forEach { tag ->
                    Row(
                        selected = tag == selectedTag,
                        label = languageLabel(tag),
                        onClick = { onSelect(tag) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun Row(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** Human-readable label for a stored language tag ([AppLocale.SYSTEM] = follow system). */
@Composable
private fun languageLabel(tag: String): String = when (tag) {
    AppLocale.SYSTEM -> stringResource(R.string.language_option_system)
    "zh" -> stringResource(R.string.language_option_chinese_simplified)
    else -> stringResource(R.string.language_option_english)
}

/** Walks the ContextWrapper chain to the hosting Activity so we can recreate it. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
