package io.grimoire.app.ui.component

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import io.grimoire.app.util.ImageSaver
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/** An extra action shown in the zoom viewer's overflow menu (e.g. cover-specific). */
data class ImageAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun ZoomableImageDialog(
    model: Any?,
    contentDescription: String?,
    onDismiss: () -> Unit,
    saveBaseName: String = "image",
    extraActions: List<ImageAction> = emptyList(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var overflowOpen by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        fun saveNow() {
            scope.launch {
                val ok = ImageSaver.saveToGallery(context, model, saveBaseName)
                Toast.makeText(
                    context,
                    if (ok) "Saved to Pictures/Grimoire" else "Couldn't save image",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        val storagePermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) saveNow()
            else Toast.makeText(context, "Storage permission needed to save", Toast.LENGTH_SHORT).show()
        }

        fun onDownload() {
            val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            if (needsLegacyPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else saveNow()
        }

        fun onShare() {
            scope.launch {
                val uri = ImageSaver.cacheForShare(context, model, saveBaseName)
                if (uri == null) {
                    Toast.makeText(context, "Couldn't share image", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share image"))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        scale = newScale
                        if (newScale > 1f) {
                            val maxX = (containerSize.width * (newScale - 1f)) / 2f
                            val maxY = (containerSize.height * (newScale - 1f)) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )

            // Top action bar. Sits above the image; its own pointerInput stops the
            // tap-to-dismiss gesture from firing when the buttons are pressed.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .pointerInput(Unit) { detectTapGestures { /* swallow */ } },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
                IconButton(onClick = ::onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                if (extraActions.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            extraActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text(action.label) },
                                    onClick = {
                                        overflowOpen = false
                                        action.onClick()
                                    },
                                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
