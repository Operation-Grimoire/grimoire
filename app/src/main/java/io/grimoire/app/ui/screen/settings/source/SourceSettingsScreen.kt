package io.grimoire.app.ui.screen.settings.source

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    // Re-check sign-in every time the screen resumes. Navigating to the login
    // WebView recreates this entry/observer, so a "wasPaused" gate would reset
    // and miss the return; an unconditional resume check is what reliably picks
    // up a freshly-completed login. checkLoginState polls + de-dupes itself.
    if (viewModel.supportsWebViewLogin) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.checkLoginState(retry = true)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(viewModel.sourceName) },
            )
        },
    ) { padding ->
        if (viewModel.preferences.isEmpty() && !viewModel.isMultiLanguage &&
            !viewModel.supportsWebViewLogin && !viewModel.isMultiHost
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (viewModel.isMultiLanguage) {
                SectionHeader("Content")
                ListItem(
                    headlineContent = { Text("Content languages") },
                    supportingContent = { Text(languageSummary) },
                    leadingContent = {
                        Icon(AppIcons.Translate, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            AppIcons.ArrowForwardIos,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToContentLanguages),
                )
            }

            if (viewModel.isMultiHost) {
                val activeHost by viewModel.activeHost.collectAsState()
                if (viewModel.isMultiLanguage) HorizontalDivider()
                SectionHeader("Mirror")
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    OutlinedTextField(
                        value = activeHost.substringAfter("://").ifEmpty { activeHost },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mirror") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        viewModel.hosts.forEach { host ->
                            DropdownMenuItem(
                                text = { Text(host.substringAfter("://").ifEmpty { host }) },
                                onClick = {
                                    viewModel.setActiveHost(host)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            if (viewModel.supportsWebViewLogin) {
                val loginState by viewModel.loginState.collectAsState()
                val signedIn = loginState == SourceSettingsViewModel.LoginUiState.SIGNED_IN
                if (viewModel.isMultiLanguage || viewModel.isMultiHost) HorizontalDivider()
                SectionHeader("Account")
                ListItem(
                    headlineContent = { Text("Sign-in status") },
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
                        Icon(AppIcons.AccountCircle, contentDescription = null)
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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

            if (viewModel.preferences.isNotEmpty() || viewModel.canValidate) {
                if (viewModel.isMultiLanguage || viewModel.supportsWebViewLogin || viewModel.isMultiHost) {
                    HorizontalDivider()
                }
                SectionHeader("Configuration")
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                val running = validation is SourceSettingsViewModel.ValidationState.Running
                OutlinedButton(
                    onClick = viewModel::validate,
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Checking…")
                    } else {
                        Text("Test configuration")
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (viewModel.preferences.isNotEmpty()) {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(if (saved) "Saved" else "Save")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
