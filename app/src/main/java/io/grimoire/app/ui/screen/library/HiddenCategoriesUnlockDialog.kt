package io.grimoire.app.ui.screen.library

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.grimoire.app.R
import kotlinx.coroutines.launch

@Composable
fun HiddenCategoriesUnlockDialog(
    biometricEnabled: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlockedByBiometric: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var biometricAttempted by remember { mutableStateOf(false) }
    val biometricTitle = stringResource(R.string.library_biometric_unlock_title)
    val biometricSubtitle = stringResource(R.string.library_biometric_unlock_subtitle)
    val incorrectPinMessage = stringResource(R.string.library_incorrect_pin)

    LaunchedEffect(biometricEnabled) {
        if (!biometricAttempted && biometricEnabled && context.canAuthenticateBiometric()) {
            biometricAttempted = true
            val activity = context.findFragmentActivity() ?: return@LaunchedEffect
            promptBiometric(
                activity = activity,
                title = biometricTitle,
                subtitle = biometricSubtitle,
                onSuccess = {
                    onUnlockedByBiometric()
                    onDismiss()
                },
                onError = { /* fall through to PIN entry */ },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_enter_pin)) },
        text = {
            Column {
                val errorMessage = error
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit); error = null },
                    label = { Text(stringResource(R.string.library_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage) }
                    } else null,
                )
                if (biometricEnabled && context.canAuthenticateBiometric()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        val activity = context.findFragmentActivity() ?: return@TextButton
                        promptBiometric(
                            activity = activity,
                            title = biometricTitle,
                            subtitle = biometricSubtitle,
                            onSuccess = {
                                onUnlockedByBiometric()
                                onDismiss()
                            },
                            onError = { msg ->
                                if (msg != null) error = msg
                            },
                        )
                    }) { Text(stringResource(R.string.library_use_biometric)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.isNotBlank() && !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val ok = onVerifyPin(pin)
                        checking = false
                        if (ok) onDismiss() else {
                            error = incorrectPinMessage
                            pin = ""
                        }
                    }
                },
            ) { Text(stringResource(R.string.library_unlock)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

fun Context.canAuthenticateBiometric(): Boolean {
    val mgr = BiometricManager.from(this)
    val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return mgr.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = (ctx as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

private fun promptBiometric(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String?) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
            // user can retry; do nothing
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(info)
}
