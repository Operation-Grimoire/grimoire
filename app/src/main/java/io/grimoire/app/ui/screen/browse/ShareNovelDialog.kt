package io.grimoire.app.ui.screen.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.ContentCopy
import io.grimoire.app.ui.icon.Share

/**
 * Popup dialog that renders a share card for the novel (cover + reading-progress stats over a
 * cover-derived gradient) and offers to share the image or copy the novel's link. The card is
 * rendered off the UI thread once when the dialog opens; [data] is a stable snapshot so the
 * render fires only once.
 */
@Composable
internal fun ShareNovelDialog(
    data: io.grimoire.app.util.NovelShareData,
    novelUrl: String,
    showCopyLink: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var rendering by remember { mutableStateOf(true) }

    LaunchedEffect(data) {
        rendering = true
        previewUri = io.grimoire.app.util.NovelShareCardRenderer.render(context, data)
        rendering = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Share",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(1080f / 1620f)
                        .clip(MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    val uri = previewUri
                    if (uri != null) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Share card preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (rendering) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "Couldn't build image",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showCopyLink) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, novelUrl)
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                "Copy link",
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Button(
                        onClick = { previewUri?.let { shareImage(context, it) } },
                        enabled = previewUri != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Share",
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
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
    context.startActivity(Intent.createChooser(send, "Share"))
}
