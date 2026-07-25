package io.grimoire.app.ui.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.AutoStories
import io.grimoire.app.ui.icon.Info

/**
 * One-time, first-run welcome. A friendly hello, then anonymous-diagnostics consent
 * (both off by default — the user opts in). Shown before the app proper (MainActivity).
 */
@Composable
fun DiagnosticsConsentScreen(
    onDone: (crashReports: Boolean, usageAnalytics: Boolean) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var crashReports by remember { mutableStateOf(false) }
    var usageAnalytics by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "welcomeStep",
        ) { current ->
            when (current) {
                0 -> WelcomeStep(onNext = { step = 1 })
                else -> ConsentStep(
                    crashReports = crashReports,
                    usageAnalytics = usageAnalytics,
                    onCrashReports = { crashReports = it },
                    onUsageAnalytics = { usageAnalytics = it },
                    onBack = { step = 0 },
                    onContinue = { onDone(crashReports, usageAnalytics) },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Hello 👋",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Welcome to Grimoire",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your open-source home for web novels — build a library, browse sources " +
                "through extensions, and read offline. Let's get a couple of things set up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
    }
}

@Composable
private fun ConsentStep(
    crashReports: Boolean,
    usageAnalytics: Boolean,
    onCrashReports: (Boolean) -> Unit,
    onUsageAnalytics: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            AppIcons.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Help improve Grimoire",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Grimoire can send anonymous diagnostics to help fix crashes and understand " +
                "which features are used. No personal data, no novel titles or links — and " +
                "you can change this anytime in Settings. Everything is off unless you turn it on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            headlineContent = { Text("Send crash reports") },
            supportingContent = { Text("Anonymous stack traces when the app crashes") },
            trailingContent = { Switch(checked = crashReports, onCheckedChange = onCrashReports) },
            modifier = Modifier.clickable { onCrashReports(!crashReports) },
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            headlineContent = { Text("Send anonymous usage analytics") },
            supportingContent = { Text("Which features get used, to guide what to build") },
            trailingContent = { Switch(checked = usageAnalytics, onCheckedChange = onUsageAnalytics) },
            modifier = Modifier.clickable { onUsageAnalytics(!usageAnalytics) },
        )

        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) { Text("Continue") }
        }
    }
}
