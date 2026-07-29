package io.grimoire.app.ui.screen.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.lang.Language
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale
import io.grimoire.app.util.ContentLanguages

/**
 * First-run welcome flow: a hello, the app UI language, then the reading
 * (content) languages. Both choices stay editable later under Settings →
 * Languages. Picking an app language recreates the activity so the flow
 * itself re-renders in it; the current step survives via [rememberSaveable].
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step = 0 }

    Scaffold(modifier = modifier) { padding ->
        when (step) {
            0 -> WelcomeStep(
                onContinue = { step = 1 },
                modifier = Modifier.padding(padding),
            )
            else -> ReadingLanguagesStep(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentTag = remember { AppLocale.storedTag(context) }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(64.dp))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.languages_app_language_title),
            style = MaterialTheme.typography.titleMedium,
        )
        AppLocale.supported.forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = tag == currentTag,
                        onClick = {
                            if (tag != currentTag) {
                                AppLocale.setTag(context, tag)
                                context.findActivity()?.recreate()
                            }
                        },
                    )
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = tag == currentTag, onClick = null)
                Text(appLanguageLabel(tag))
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun ReadingLanguagesStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val selected by viewModel.selected.collectAsState()

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.onboarding_reading_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_reading_helper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(ContentLanguages.SELECTABLE, key = { it.code }) { lang ->
                LanguageRow(
                    language = lang,
                    checked = lang in selected,
                    onClick = { viewModel.toggle(lang) },
                )
            }
        }
        Button(
            onClick = viewModel::finish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.onboarding_get_started))
        }
    }
}

@Composable
private fun LanguageRow(
    language: Language,
    checked: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(language.displayName) },
        supportingContent = language.nativeName
            .takeIf { it != language.displayName }
            ?.let { native -> { Text(native) } },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** Label for a stored app-language tag ([AppLocale.SYSTEM] = follow system). */
@Composable
private fun appLanguageLabel(tag: String): String = when (tag) {
    AppLocale.SYSTEM -> stringResource(R.string.language_option_system)
    "zh" -> stringResource(R.string.language_option_chinese_simplified)
    else -> stringResource(R.string.language_option_english)
}

/** Walks the ContextWrapper chain to the hosting Activity so we can recreate it. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
