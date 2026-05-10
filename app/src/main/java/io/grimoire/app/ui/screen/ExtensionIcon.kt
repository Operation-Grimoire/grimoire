package io.grimoire.app.ui.screen

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * Shows the installed APK's launcher icon, falling back to a lang-code badge when unavailable
 * (e.g. extension not yet installed).
 */
@Composable
internal fun ExtensionIcon(packageName: String, lang: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(40.dp),
        )
    } else {
        LangBadge(lang = lang, modifier = modifier)
    }
}

@Composable
internal fun LangBadge(lang: String, modifier: Modifier = Modifier) {
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
