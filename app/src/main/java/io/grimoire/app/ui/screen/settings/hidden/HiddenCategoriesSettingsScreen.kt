package io.grimoire.app.ui.screen.settings.hidden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.library.HiddenCategoriesUnlockDialog
import io.grimoire.app.ui.screen.library.canAuthenticateBiometric
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenCategoriesSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HiddenCategoriesSettingsViewModel = hiltViewModel(),
) {
    val hasPin by viewModel.hasPin.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val includeHiddenInAll by viewModel.includeHiddenInAll.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSetPin by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Hidden categories") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 8.dp)) {
            when {
                !hasPin -> item {
                    ListItem(
                        headlineContent = { Text("Set up a PIN") },
                        supportingContent = {
                            Text("A PIN is required to hide categories and unlock them later.")
                        },
                    )
                    Button(
                        onClick = { showSetPin = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Set up PIN") }
                }

                !isUnlocked -> item {
                    ListItem(
                        headlineContent = { Text("Locked") },
                        supportingContent = { Text("Unlock to manage hidden categories.") },
                    )
                    Button(
                        onClick = { showUnlock = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Unlock") }
                }

                else -> {
                    item {
                        ListItem(
                            headlineContent = { Text("Change PIN") },
                            modifier = Modifier.padding(top = 4.dp),
                            trailingContent = {
                                TextButton(onClick = { showChangePin = true }) { Text("Change") }
                            },
                        )
                    }
                    item {
                        val biometricAvailable = context.canAuthenticateBiometric()
                        ListItem(
                            headlineContent = { Text("Use biometric unlock") },
                            supportingContent = {
                                Text(
                                    if (biometricAvailable) "Fingerprint or face unlock instead of PIN"
                                    else "Not available — no biometrics enrolled"
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = biometricEnabled && biometricAvailable,
                                    enabled = biometricAvailable,
                                    onCheckedChange = { viewModel.setBiometricEnabled(it) },
                                )
                            },
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Show hidden in \"All\" tab") },
                            supportingContent = {
                                Text("When unlocked, also list hidden novels under the All tab")
                            },
                            trailingContent = {
                                Switch(
                                    checked = includeHiddenInAll,
                                    onCheckedChange = { viewModel.setIncludeHiddenInAll(it) },
                                )
                            },
                        )
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            "Categories",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(categories) { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name) },
                            supportingContent = {
                                if (cat.isDefault) Text("Default — cannot be hidden")
                            },
                            trailingContent = {
                                Switch(
                                    checked = cat.isHidden,
                                    enabled = !cat.isDefault,
                                    onCheckedChange = { viewModel.setCategoryHidden(cat, it) },
                                )
                            },
                        )
                    }
                    item {
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { showRemoveConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) { Text("Remove PIN and unhide all") }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showSetPin) {
        PinEntryDialog(
            title = "Set up PIN",
            requireConfirm = true,
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showSetPin = false
            },
            onDismiss = { showSetPin = false },
        )
    }

    if (showChangePin) {
        PinEntryDialog(
            title = "Change PIN",
            requireConfirm = true,
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showChangePin = false
            },
            onDismiss = { showChangePin = false },
        )
    }

    if (showUnlock) {
        HiddenCategoriesUnlockDialog(
            biometricEnabled = biometricEnabled,
            onVerifyPin = { pin -> viewModel.verifyAndUnlock(pin) },
            onUnlockedByBiometric = { viewModel.unlockFromBiometric() },
            onDismiss = { showUnlock = false },
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove PIN?") },
            text = { Text("All hidden categories will become visible to anyone using the app.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPin()
                    showRemoveConfirm = false
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PinEntryDialog(
    title: String,
    requireConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text("PIN (4–8 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (requireConfirm) {
                    val errorMessage = error
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter(Char::isDigit).take(8); error = null },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            { Text(errorMessage) }
                        } else null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in 4..8 && (!requireConfirm || confirm.length == pin.length),
                onClick = {
                    if (requireConfirm && pin != confirm) {
                        error = "PINs don't match"
                    } else {
                        onConfirm(pin)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
