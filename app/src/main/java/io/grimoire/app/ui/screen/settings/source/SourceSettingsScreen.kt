package io.grimoire.app.ui.screen.settings.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
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
    onNavigateToContentLanguages: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceSettingsViewModel = hiltViewModel(),
) {
    val values by viewModel.values.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val validation by viewModel.validation.collectAsState()
    val languageSummary by viewModel.languageSummary.collectAsState()

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
        if (viewModel.preferences.isEmpty() && !viewModel.isMultiLanguage &&
            !viewModel.supportsWebViewLogin
        ) {
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

            if (viewModel.isMultiLanguage) {
                ListItem(
                    headlineContent = { Text("Content languages") },
                    supportingContent = { Text(languageSummary) },
                    leadingContent = {
                        Icon(Icons.Default.Translate, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToContentLanguages),
                )
            }

            if (viewModel.supportsWebViewLogin) {
                val loginState by viewModel.loginState.collectAsState()
                val signedIn = loginState == SourceSettingsViewModel.LoginUiState.SIGNED_IN
                ListItem(
                    headlineContent = { Text("Account") },
                    supportingContent = {
                        Text(
                            when (loginState) {
                                SourceSettingsViewModel.LoginUiState.SIGNED_IN -> "Signed in"
                                SourceSettingsViewModel.LoginUiState.SIGNED_OUT -> "Not signed in"
                                SourceSettingsViewModel.LoginUiState.UNKNOWN -> "Checking…"
                            },
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNavigateToLogin, modifier = Modifier.weight(1f)) {
                        Text(if (signedIn) "Log in again" else "Log in")
                    }
                    if (signedIn) {
                        OutlinedButton(
                            onClick = viewModel::logout,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Log out")
                        }
                    }
                }
            }

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
                        val checked = values[pref.key].toBoolean()
                        ListItem(
                            headlineContent = { Text(pref.title) },
                            supportingContent = pref.summary?.let { summary -> { Text(summary) } },
                            trailingContent = {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = {
                                        viewModel.update(pref.key, it.toString())
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.update(pref.key, (!checked).toString())
                            },
                        )
                    }
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

            if (viewModel.preferences.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saved) "Saved" else "Save")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
