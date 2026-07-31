package io.grimoire.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.grimoire.app.R
import io.grimoire.app.ui.icon.*

/**
 * Error state for a Cloudflare challenge the silent interceptor couldn't
 * solve: tells the user to solve it in the WebView and offers the CTA.
 * Pair with [AutoRetryOnReturn] so coming back from the WebView refreshes
 * without another tap.
 */
@Composable
internal fun CloudflareBlockedCard(
    sourceName: String,
    onOpenWebView: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            AppIcons.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.source_browse_cloudflare_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.source_browse_cloudflare_description, sourceName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenWebView) {
            Icon(AppIcons.Language, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_in_webview))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

/**
 * Fires [onRetry] when this destination comes back to the foreground while
 * [blocked] is still true — i.e. the user went to the WebView (or anywhere
 * else) from a Cloudflare-blocked screen and returned. The initial resume on
 * entering the screen never triggers a retry.
 */
@Composable
internal fun AutoRetryOnReturn(blocked: () -> Boolean, onRetry: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var seenResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (seenResume && blocked()) onRetry()
                seenResume = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
