package io.grimoire.app.ui.screen.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.grimoire.app.R
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.ContentCopy
import io.grimoire.app.ui.icon.Share

/**
 * Bottom sheet that renders a share card for the novel (cover + reading-progress stats over a
 * cover-derived gradient) and offers to share the image or copy the novel's link as full-width
 * action rows — narrow screens can't clip them like the old dialog's side-by-side buttons did.
 * The card is rendered off the UI thread once when the sheet opens; [data] is a stable snapshot
 * so the render fires only once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareNovelDialog(
    data: io.grimoire.app.util.NovelShareData,
    novelUrl: String,
    showCopyLink: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Fully expanded from the start — the action rows sit under the tall
    // preview and must be visible without a drag.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var rendering by remember { mutableStateOf(true) }

    LaunchedEffect(data) {
        rendering = true
        previewUri = io.grimoire.app.util.NovelShareCardRenderer.render(context, data)
        rendering = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterHorizontally)
                    .aspectRatio(1080f / 1620f)
                    .clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                val uri = previewUri
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.share_preview_content_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (rendering) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.share_build_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            ShareAction(
                icon = AppIcons.Share,
                label = stringResource(R.string.share_image_action),
                enabled = previewUri != null,
            ) {
                previewUri?.let { shareImage(context, it) }
            }
            if (showCopyLink) {
                ShareAction(
                    icon = AppIcons.ContentCopy,
                    label = stringResource(R.string.share_copy_link),
                    enabled = true,
                ) {
                    copyToClipboard(context, novelUrl)
                    Toast.makeText(context, context.getString(R.string.share_link_copied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun ShareAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            headlineColor = color,
            leadingIconColor = color,
        ),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("novel_url", text))
}

private fun shareImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share)))
}
