package io.grimoire.app.ui.screen.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.grimoire.api.model.lang.Language
import io.grimoire.app.R
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.Language as LanguageIcon

@Composable
internal fun OnboardingLanguagesStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val selected by viewModel.selected.collectAsState()
    val query by viewModel.languageQuery.collectAsState()
    val languages by viewModel.filteredLanguages.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        OnboardingStepHeader(
            icon = AppIcons.LanguageIcon,
            title = stringResource(R.string.onboarding_reading_title),
            subtitle = stringResource(R.string.onboarding_reading_helper),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            AppSearchField(
                value = query,
                onValueChange = viewModel::setLanguageQuery,
                placeholder = stringResource(R.string.onboarding_languages_search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                val badgeText = if (selected.isEmpty()) {
                    stringResource(R.string.onboarding_languages_all)
                } else {
                    pluralStringResource(R.plurals.onboarding_languages_selected, selected.size, selected.size)
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (selected.isNotEmpty()) {
                TextButton(onClick = viewModel::clearLanguages) {
                    Text(stringResource(R.string.action_clear))
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(languages, key = { it.code }) { lang ->
                LanguageRow(
                    language = lang,
                    checked = lang in selected,
                    onClick = { viewModel.toggle(lang) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: Language,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = {
            Text(
                text = language.displayName,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = language.nativeName
            .takeIf { it != language.displayName }
            ?.let { native -> { Text(native) } },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    )
}
