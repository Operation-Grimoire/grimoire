package io.grimoire.app.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

/**
 * Shows the installed APK's launcher icon (loaded via Coil from an android.resource URI),
 * falling back to the remote [iconUrl] from index.json, then a lang-code badge.
 */
@Composable
fun ExtensionIcon(
    packageName: String,
    lang: String,
    iconUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Resolve the installed icon to a resource URI (no bitmap decode here; Coil does it off-thread).
    val model = remember(packageName, iconUrl) {
        val local = runCatching {
            val res = context.packageManager.getApplicationInfo(packageName, 0).icon
            if (res != 0) Uri.parse("android.resource://$packageName/$res") else null
        }.getOrNull()
        local ?: iconUrl
    }

    if (model == null) {
        LangBadge(lang = lang, modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.size(40.dp),
        error = { LangBadge(lang = lang) },
    )
}

@Composable
fun LangBadge(lang: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = lang.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
