package io.grimoire.app.ui.screen.settings.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.api.source.SourcePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceSettingsViewModel = hiltViewModel(),
) {
    val values by viewModel.values.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val validation by viewModel.validation.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(viewModel.sourceName) },
            )
        },
    ) { padding ->
        if (viewModel.preferences.isEmpty() && !viewModel.isMultiLanguage) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "This source has no settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            viewModel.preferences.forEach { pref ->
                when (pref) {
                    is SourcePreference.EditText -> {
                        OutlinedTextField(
                            value = values[pref.key].orEmpty(),
                            onValueChange = { viewModel.update(pref.key, it) },
                            label = { Text(pref.title) },
                            supportingText = pref.summary?.let { summary -> { Text(summary) } },
                            singleLine = true,
                            visualTransformation = if (pref.isPassword) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is SourcePreference.Switch -> {
                        ListItem(
                            headlineContent = { Text(pref.title) },
                            supportingContent = pref.summary?.let { summary -> { Text(summary) } },
                            trailingContent = {
                                Switch(
                                    checked = values[pref.key].toBoolean(),
                                    onCheckedChange = {
                                        viewModel.update(pref.key, it.toString())
                                    },
                                )
                            },
                        )
                    }
                }
            }

            if (viewModel.isMultiLanguage) {
                val enabled by viewModel.enabledLanguages.collectAsState()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Content languages",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Only show results in the selected languages. Leave all " +
                        "unchecked to show every language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                viewModel.allLanguages.forEach { lang ->
                    ListItem(
                        headlineContent = { Text(lang) },
                        trailingContent = {
                            Checkbox(
                                checked = lang.trim().lowercase() in enabled,
                                onCheckedChange = { viewModel.toggleLanguage(lang) },
                            )
                        },
                        modifier = Modifier.clickable { viewModel.toggleLanguage(lang) },
                    )
                }
            }

            if (viewModel.canValidate) {
                Spacer(Modifier.height(8.dp))
                val running = validation is SourceSettingsViewModel.ValidationState.Running
                OutlinedButton(
                    onClick = viewModel::validate,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.height(0.dp))
                        Text("  Checking…")
                    } else {
                        Text("Test login")
                    }
                }
                (validation as? SourceSettingsViewModel.ValidationState.Done)?.let { result ->
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saved) "Saved" else "Save")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
