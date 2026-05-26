package io.grimoire.app.ui.screen.settings.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.auth.github.AuthFailure
import io.grimoire.app.auth.github.GitHubAuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubAuthScreen(
    onNavigateBack: () -> Unit,
    viewModel: GitHubAuthViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("GitHub account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Connect a GitHub account to install extensions from your own private repositories. " +
                    "Your access token is stored only on this device and is sent only to github.com. " +
                    "Revoke any time at github.com/settings/applications.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!viewModel.isClientConfigured) {
                MissingClientIdCard()
                return@Column
            }

            when (val s = state) {
                GitHubAuthState.Disconnected -> DisconnectedBody(onConnect = viewModel::connect)
                is GitHubAuthState.AwaitingUser -> AwaitingBody(
                    userCode = s.challenge.userCode,
                    verificationUri = s.challenge.verificationUri,
                    onCopy = { copyToClipboard(context, s.challenge.userCode) },
                    onOpenBrowser = { openInBrowser(context, s.challenge.verificationUri) },
                    onCancel = viewModel::cancel,
                )
                is GitHubAuthState.Connected -> ConnectedBody(
                    login = s.login,
                    onDisconnect = viewModel::disconnect,
                )
                is GitHubAuthState.Failed -> FailedBody(
                    failure = s.reason,
                    onDismiss = viewModel::dismissError,
                    onRetry = viewModel::connect,
                )
            }
        }
    }
}

@Composable
private fun DisconnectedBody(onConnect: () -> Unit) {
    Text("Not connected.", style = MaterialTheme.typography.titleMedium)
    Button(onClick = onConnect) { Text("Connect GitHub") }
}

@Composable
private fun AwaitingBody(
    userCode: String,
    verificationUri: String,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Enter this code on github.com", style = MaterialTheme.typography.titleMedium)
            Text(
                userCode,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                verificationUri,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Button(onClick = onOpenBrowser, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Open github.com/login/device")
    }
    OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.ContentCopy, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Copy code")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(
            "Waiting for you to finish in the browser…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    TextButton(onClick = onCancel) { Text("Cancel") }
}

@Composable
private fun ConnectedBody(login: String, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Connected", style = MaterialTheme.typography.titleMedium)
            Text("@$login", style = MaterialTheme.typography.bodyLarge)
        }
    }
    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
}

@Composable
private fun FailedBody(failure: AuthFailure, onDismiss: () -> Unit, onRetry: () -> Unit) {
    Text("Authorization failed", style = MaterialTheme.typography.titleMedium)
    Text(
        failure.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Button(onClick = onRetry) { Text("Try again") }
    TextButton(onClick = onDismiss) { Text("Dismiss") }
}

@Composable
private fun MissingClientIdCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "This build has no OAuth client ID",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Set GITHUB_OAUTH_CLIENT_ID in gradle.properties (or as an environment variable) and rebuild.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("user_code", text))
}

private fun openInBrowser(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
